package com.example.jenkinslearning.data.repository

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.core.common.SecretRedactor
import com.example.jenkinslearning.core.network.HttpClientFactory
import com.example.jenkinslearning.data.local.InMemorySessionRepository
import com.example.jenkinslearning.data.remote.JenkinsApiProvider
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.error.UrlProblem
import com.example.jenkinslearning.domain.model.BuildStatus
import com.example.jenkinslearning.domain.model.ConnectionSettings
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.SessionCredentials
import com.example.jenkinslearning.testutil.FakeConnectionSettingsRepository
import com.example.jenkinslearning.testutil.resource
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Credentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration test: the real Retrofit + OkHttp + Gson stack talking to a local
 * MockWebServer that answers like Jenkins. No real Jenkins is needed.
 */
class RemoteJenkinsRepositoryTest {

    private val token = "11a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7"
    private lateinit var server: MockWebServer
    private lateinit var session: InMemorySessionRepository
    private lateinit var settings: FakeConnectionSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session = InMemorySessionRepository().apply { set(SessionCredentials("dev", token)) }
        // A context path (/jenkins/) checks that relative endpoint paths keep it.
        settings = FakeConnectionSettingsRepository(
            ConnectionSettings(server.url("/jenkins/").toString(), "dev", DataMode.LIVE),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repository(maxConsoleBytes: Long = RemoteJenkinsRepository.DEFAULT_MAX_CONSOLE_BYTES) = RemoteJenkinsRepository(
        apiProvider = JenkinsApiProvider(HttpClientFactory.create(enableLogging = false), Gson(), session),
        settingsRepository = settings,
        redactor = SecretRedactor { listOfNotNull(session.current()?.apiToken) },
        ioDispatcher = Dispatchers.IO,
        maxConsoleBytes = maxConsoleBytes,
    )

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `server overview is parsed, authenticated and keeps the context path`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Jenkins", "2.528.1")
                .body(resource("jenkins/server.json"))
                .build(),
        )

        val overview = (repository().getServerOverview() as Outcome.Success).value

        assertEquals("2.528.1", overview.jenkinsVersion)
        assertEquals(DataSource.LIVE, overview.source)
        assertEquals(4, overview.jobs.size)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/jenkins/api/json", request.url.encodedPath)
        assertTrue(request.url.queryParameter("tree")!!.startsWith("description,jobs["))
        assertEquals(Credentials.basic("dev", token), request.headers["Authorization"])
    }

    @Test
    fun `no token means no Authorization header`() = runTest {
        session.clear()
        server.enqueue(json("""{"jobs":[]}"""))

        repository().getServerOverview()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `HTTP errors map to typed errors and never contain the token`() = runTest {
        val cases = listOf(
            MockResponse(code = 401) to JenkinsError.Unauthorized,
            MockResponse(code = 403) to JenkinsError.Forbidden,
            MockResponse(code = 404) to JenkinsError.NotFound,
            MockResponse.Builder().code(429).addHeader("Retry-After", "30").build() to JenkinsError.RateLimited(30),
            MockResponse(code = 503) to JenkinsError.ServerError(503),
        )
        val repo = repository()
        for ((response, expected) in cases) {
            server.enqueue(response)
            val result = repo.getServerOverview()
            assertEquals(Outcome.Failure(expected), result)
            assertFalse(result.toString().contains(token))
        }
    }

    @Test
    fun `an HTML page instead of JSON is an invalid response`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "<html><body>Sign in to Jenkins</body></html>"))
        assertEquals(Outcome.Failure(JenkinsError.InvalidResponse), repository().getServerOverview())
    }

    @Test
    fun `job names with folders and spaces are encoded per segment`() = runTest {
        server.enqueue(json(resource("jenkins/job.json")))

        repository().getJobDetails("android/my app")

        assertEquals("/jenkins/job/android/job/my%20app/api/json", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `build details build artifact links from the configured URL`() = runTest {
        server.enqueue(json(resource("jenkins/build.json")))

        val details = (repository().getBuildDetails("JenkinsLearningApp", 42) as Outcome.Success).value

        assertEquals(BuildStatus.UNSTABLE, details.status)
        assertEquals(58, details.tests?.total)
        assertTrue(details.artifacts.first().downloadUrl.startsWith(server.url("/jenkins/job/JenkinsLearningApp/42/artifact/").toString()))
        assertEquals("/jenkins/job/JenkinsLearningApp/42/api/json", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `trigger fetches a crumb and posts it to build`() = runTest {
        server.enqueue(json(resource("jenkins/job.json")))
        server.enqueue(json("""{"crumb":"abc123","crumbRequestField":"Jenkins-Crumb"}"""))
        server.enqueue(
            MockResponse.Builder().code(201).addHeader("Location", server.url("/jenkins/queue/item/77/").toString()).build(),
        )

        val queued = (repository().triggerBuild("JenkinsLearningApp") as Outcome.Success).value

        assertEquals(77L, queued.queueItemId)
        assertEquals(DataSource.LIVE, queued.source)
        assertEquals("/jenkins/job/JenkinsLearningApp/api/json", server.takeRequest().url.encodedPath)
        assertEquals("/jenkins/crumbIssuer/api/json", server.takeRequest().url.encodedPath)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/jenkins/job/JenkinsLearningApp/build", post.url.encodedPath)
        assertEquals("abc123", post.headers["Jenkins-Crumb"])
    }

    @Test
    fun `parameterized jobs use buildWithParameters, and a disabled crumb issuer is fine`() = runTest {
        server.enqueue(json(resource("jenkins/job_parameterized.json")))
        server.enqueue(MockResponse(code = 404)) // crumb issuer disabled
        server.enqueue(MockResponse(code = 201))

        val result = repository().triggerBuild("JenkinsLearningApp")

        assertTrue(result is Outcome.Success)
        server.takeRequest()
        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("/jenkins/job/JenkinsLearningApp/buildWithParameters", post.url.encodedPath)
        assertNull(post.headers["Jenkins-Crumb"])
    }

    @Test
    fun `a job that is not buildable is never posted`() = runTest {
        server.enqueue(json("""{"name":"legacy","buildable":false}"""))

        assertEquals(Outcome.Failure(JenkinsError.JobNotBuildable), repository().triggerBuild("legacy"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `trigger without Job-Build permission reports Forbidden, not success`() = runTest {
        server.enqueue(json(resource("jenkins/job.json")))
        server.enqueue(MockResponse(code = 404))
        server.enqueue(MockResponse(code = 403))

        assertEquals(Outcome.Failure(JenkinsError.Forbidden), repository().triggerBuild("JenkinsLearningApp"))
    }

    @Test
    fun `console output is redacted, including the user's own token`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = "Started\ncurl -u dev:$token https://x\nDB_PASSWORD=supersecret\nFinished: SUCCESS\n"),
        )

        val log = (repository().getConsoleLog("JenkinsLearningApp", 42) as Outcome.Success).value

        assertFalse(log.text.contains(token))
        assertFalse(log.text.contains("supersecret"))
        assertTrue(log.text.contains("Finished: SUCCESS"))
        assertTrue(log.redactions >= 2)
        assertFalse(log.truncated)
        assertEquals("/jenkins/job/JenkinsLearningApp/42/consoleText", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `long console output keeps only the tail`() = runTest {
        val body = (1..2_000).joinToString("\n") { "line $it" } + "\nFinished: FAILURE\n"
        server.enqueue(MockResponse(code = 200, body = body))

        val log = (repository(maxConsoleBytes = 1_024).getConsoleLog("JenkinsLearningApp", 1) as Outcome.Success).value

        assertTrue(log.truncated)
        assertTrue(log.text.length <= 1_024)
        assertTrue(log.text.endsWith("Finished: FAILURE\n"))
        assertTrue(log.text.startsWith("line ")) // the partial first line was dropped
    }

    @Test
    fun `an unreachable server maps to HostUnreachable`() = runTest {
        server.close()
        assertEquals(Outcome.Failure(JenkinsError.HostUnreachable), repository().getServerOverview())
    }

    @Test
    fun `an invalid configured URL fails before any request`() = runTest {
        settings.emit(ConnectionSettings("http://user:pw@host/", "dev", DataMode.LIVE))
        assertEquals(
            Outcome.Failure(JenkinsError.InvalidServerUrl(UrlProblem.ContainsCredentials)),
            repository().getServerOverview(),
        )
        assertEquals(0, server.requestCount)
    }
}
