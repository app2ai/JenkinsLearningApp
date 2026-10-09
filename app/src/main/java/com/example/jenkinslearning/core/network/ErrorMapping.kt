package com.example.jenkinslearning.core.network

import com.example.jenkinslearning.domain.error.JenkinsError
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/** Maps an unsuccessful HTTP status to a [JenkinsError]. */
object HttpErrorMapper {

    fun fromStatus(code: Int, retryAfterHeader: String? = null): JenkinsError = when (code) {
        401 -> JenkinsError.Unauthorized
        403 -> JenkinsError.Forbidden
        404 -> JenkinsError.NotFound
        429 -> JenkinsError.RateLimited(parseRetryAfterSeconds(retryAfterHeader))
        in 500..599 -> JenkinsError.ServerError(code)
        else -> JenkinsError.UnexpectedStatus(code)
    }

    /**
     * Retry-After may be delay-seconds or an HTTP date. Only the
     * delay-seconds form is parsed. A date returns null and the UI just says "later".
     */
    internal fun parseRetryAfterSeconds(header: String?): Long? =
        header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
}

/**
 * Maps exceptions to [JenkinsError] without ever reading their messages,
 * except for the one platform-defined cleartext marker. Messages from
 * networking libraries can contain URLs or server output.
 */
object ThrowableErrorMapper {

    fun map(throwable: Throwable): JenkinsError = when {
        // Checked first: Gson's MalformedJsonException is an IOException, and an empty
        // body makes Gson throw EOFException. Both mean "not the JSON we expected".
        throwable.isJsonError() || throwable is EOFException -> JenkinsError.InvalidResponse
        else -> mapByType(throwable)
    }

    private fun mapByType(throwable: Throwable): JenkinsError = when (throwable) {
        is SocketTimeoutException -> JenkinsError.Timeout
        is UnknownHostException, is ConnectException, is NoRouteToHostException -> JenkinsError.HostUnreachable
        // Android throws UnknownServiceException("CLEARTEXT communication to <host> not permitted ...")
        // when the network security config blocks http://.
        is UnknownServiceException ->
            if (throwable.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                JenkinsError.CleartextBlocked
            } else {
                JenkinsError.Unexpected("UnknownServiceException")
            }
        is SSLException -> JenkinsError.TlsFailure
        // OkHttp's call timeout surfaces as InterruptedIOException("timeout").
        is InterruptedIOException -> JenkinsError.Timeout
        is IOException -> JenkinsError.HostUnreachable
        // Thrown by JenkinsJobPath / Retrofit for an unusable job name or path.
        is IllegalArgumentException -> JenkinsError.NotFound
        else -> JenkinsError.Unexpected(throwable.javaClass.simpleName)
    }

    // Checked by class name so this mapper doesn't depend on Gson.
    private fun Throwable.isJsonError(): Boolean =
        generateSequence(this) { it.cause }.take(5).any { it.javaClass.name.startsWith("com.google.gson.") }
}
