package com.example.jenkinslearning.data.repository

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.core.common.SecretRedactor
import com.example.jenkinslearning.core.config.JenkinsUrlParser
import com.example.jenkinslearning.core.config.UrlParseResult
import com.example.jenkinslearning.core.network.HttpErrorMapper
import com.example.jenkinslearning.core.network.JenkinsJobPath
import com.example.jenkinslearning.core.network.ThrowableErrorMapper
import com.example.jenkinslearning.data.mapper.JenkinsMappers
import com.example.jenkinslearning.data.remote.JenkinsApi
import com.example.jenkinslearning.data.remote.JenkinsApiProvider
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.ConsoleLog
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.domain.repository.ConnectionSettingsRepository
import com.example.jenkinslearning.domain.repository.JenkinsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Response

/**
 * Talks to a real Jenkins server over its REST API.
 *
 * Every public function returns an [Outcome]. HTTP errors and exceptions are
 * mapped to [JenkinsError] here, so nothing above the data layer ever sees a
 * Retrofit/OkHttp type, a raw exception message or a response body.
 */
class RemoteJenkinsRepository(
    private val apiProvider: JenkinsApiProvider,
    private val settingsRepository: ConnectionSettingsRepository,
    private val redactor: SecretRedactor,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxConsoleBytes: Long = DEFAULT_MAX_CONSOLE_BYTES,
) : JenkinsRepository {

    override suspend fun getServerOverview(): Outcome<ServerOverview> = withApi { api, baseUrl ->
        val response = api.getServer()
        response.toOutcome { body ->
            JenkinsMappers.toServerOverview(body, baseUrl, jenkinsVersion = response.headers()["X-Jenkins"])
        }
    }

    override suspend fun getJobDetails(jobFullName: String): Outcome<JobDetails> = withApi { api, _ ->
        api.getJob(JenkinsJobPath.encode(jobFullName)).toOutcome { JenkinsMappers.toJobDetails(it, jobFullName) }
    }

    override suspend fun getBuildDetails(jobFullName: String, buildNumber: Int): Outcome<BuildDetails> =
        withApi { api, baseUrl ->
            api.getBuild(JenkinsJobPath.encode(jobFullName), buildNumber).toOutcome {
                JenkinsMappers.toBuildDetails(it, jobFullName, buildNumber, baseUrl)
            }
        }

    override suspend fun getConsoleLog(jobFullName: String, buildNumber: Int): Outcome<ConsoleLog> =
        withApi { api, _ ->
            val response = api.getConsoleText(JenkinsJobPath.encode(jobFullName), buildNumber)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withApi Outcome.Failure(response.toError())
            }
            val body = response.body() ?: return@withApi Outcome.Failure(JenkinsError.InvalidResponse)
            // Reading the stream blocks, so it runs on the IO dispatcher.
            val (raw, truncated) = withContext(ioDispatcher) { body.readTail(maxConsoleBytes) }
            val redacted = redactor.redact(raw)
            Outcome.Success(ConsoleLog(redacted.text, truncated, redacted.redactions, DataSource.LIVE))
        }

    override suspend fun triggerBuild(jobFullName: String): Outcome<QueuedBuild> = withApi { api, _ ->
        val jobPath = JenkinsJobPath.encode(jobFullName)

        // 1. Check the job, so we know whether it's buildable and parameterized.
        val jobResponse = api.getJob(jobPath)
        if (!jobResponse.isSuccessful) return@withApi Outcome.Failure(jobResponse.toError())
        val job = jobResponse.body() ?: return@withApi Outcome.Failure(JenkinsError.InvalidResponse)
        if (job.buildable != true) return@withApi Outcome.Failure(JenkinsError.JobNotBuildable)
        val parameterized = job.properties.orEmpty().any { !it.parameterDefinitions.isNullOrEmpty() }

        // 2. CSRF crumb (if the server issues one), then POST.
        val headers = crumbHeaders(api)
        val response = if (parameterized) {
            api.triggerBuildWithParameters(jobPath, headers)
        } else {
            api.triggerBuild(jobPath, headers)
        }

        when {
            response.isSuccessful -> Outcome.Success(
                QueuedBuild(
                    jobFullName = jobFullName,
                    queueItemId = JenkinsMappers.queueItemIdFromLocation(response.headers()["Location"]),
                    source = DataSource.LIVE,
                ),
            )
            response.code() == 400 && !parameterized -> Outcome.Failure(JenkinsError.BuildRequiresParameters)
            else -> Outcome.Failure(response.toError())
        }
    }

    /**
     * Jenkins exempts requests authenticated with an API token from CSRF
     * checks, but servers configured differently (or anonymous users) still
     * need a crumb. Fetch one if available; a 404 means the issuer is disabled.
     * Failure here is not fatal: the POST's own response decides the outcome.
     */
    private suspend fun crumbHeaders(api: JenkinsApi): Map<String, String> = try {
        val response = api.getCrumb()
        val crumb = response.body()?.takeIf { response.isSuccessful }
        val field = crumb?.crumbRequestField
        val value = crumb?.crumb
        if (!field.isNullOrBlank() && !value.isNullOrBlank()) mapOf(field to value) else emptyMap()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyMap()
    }

    /** Resolves the configured base URL and runs [block], mapping any exception to a [JenkinsError]. */
    private suspend fun <T> withApi(block: suspend (api: JenkinsApi, baseUrl: String) -> Outcome<T>): Outcome<T> {
        val settings = settingsRepository.settings.first()
        val baseUrl = when (val parsed = JenkinsUrlParser.parse(settings.baseUrl)) {
            is UrlParseResult.Valid -> parsed.baseUrl
            is UrlParseResult.Invalid -> return Outcome.Failure(JenkinsError.InvalidServerUrl(parsed.problem))
        }
        return try {
            block(apiProvider.forBaseUrl(baseUrl), baseUrl)
        } catch (e: CancellationException) {
            throw e // Never swallow cancellation: it breaks structured concurrency.
        } catch (e: Exception) {
            Outcome.Failure(ThrowableErrorMapper.map(e))
        }
    }

    private inline fun <T, R> Response<T>.toOutcome(transform: (T) -> R): Outcome<R> {
        if (!isSuccessful) {
            errorBody()?.close()
            return Outcome.Failure(toError())
        }
        val body = body() ?: return Outcome.Failure(JenkinsError.InvalidResponse)
        return Outcome.Success(transform(body))
    }

    private fun Response<*>.toError(): JenkinsError =
        HttpErrorMapper.fromStatus(code(), headers()["Retry-After"])

    companion object {
        /** Console logs can be many MB. Keep the last 256 KB, where failures usually are. */
        const val DEFAULT_MAX_CONSOLE_BYTES: Long = 256L * 1024L
    }
}

/** Reads the whole stream but keeps only the last [maxBytes]. Returns (text, truncated). */
internal fun ResponseBody.readTail(maxBytes: Long): Pair<String, Boolean> = use { body ->
    val source = body.source()
    val buffer = Buffer()
    var truncated = false
    while (source.read(buffer, 8_192L) != -1L) {
        if (buffer.size > maxBytes) {
            buffer.skip(buffer.size - maxBytes)
            truncated = true
        }
    }
    val text = buffer.readUtf8()
    // After truncation the first line is probably partial; drop it.
    (if (truncated) text.substringAfter('\n', text) else text) to truncated
}
