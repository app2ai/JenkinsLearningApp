package com.example.jenkinslearning.domain.usecase

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.data.local.InMemorySessionRepository
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.error.UrlProblem
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.repository.JenkinsRepository
import com.example.jenkinslearning.testutil.FakeConnectionSettingsRepository
import com.example.jenkinslearning.testutil.FakeJenkinsRepository
import com.example.jenkinslearning.testutil.Fixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCasesTest {

    private val repository = FakeJenkinsRepository()

    @Test
    fun `GetJobs drops folders, sorts by name and keeps the data source`() = runTest {
        repository.overviewResult = Outcome.Success(
            Fixtures.overview(
                listOf(Fixtures.job("zeta"), Fixtures.job("folder", isFolder = true), Fixtures.job("Alpha")),
                source = DataSource.SAMPLE,
            ),
        )

        val result = GetJobsUseCase(repository)() as Outcome.Success

        assertEquals(listOf("Alpha", "zeta"), result.value.jobs.map { it.fullName })
        assertEquals(DataSource.SAMPLE, result.value.source)
    }

    @Test
    fun `GetDashboard passes repository errors through unchanged`() = runTest {
        repository.overviewResult = Outcome.Failure(JenkinsError.Unauthorized)
        assertEquals(Outcome.Failure(JenkinsError.Unauthorized), GetDashboardUseCase(repository)())
    }

    @Test
    fun `build use cases reject invalid arguments without calling the repository`() = runTest {
        assertEquals(Outcome.Failure(JenkinsError.NotFound), GetBuildDetailsUseCase(repository)("app", 0))
        assertEquals(Outcome.Failure(JenkinsError.NotFound), GetConsoleLogUseCase(repository)(" ", 3))
        assertEquals(Outcome.Failure(JenkinsError.NotFound), GetJobDetailsUseCase(repository)(""))
        assertEquals(0, repository.buildDetailsCalls + repository.consoleCalls + repository.jobDetailsCalls)
    }

    @Test
    fun `TriggerBuild refuses non-buildable jobs and never contacts Jenkins`() = runTest {
        val jenkins = mockk<JenkinsRepository>()

        val result = TriggerBuildUseCase(jenkins)(Fixtures.job("disabled", buildable = false))

        assertEquals(Outcome.Failure(JenkinsError.JobNotBuildable), result)
        coVerify(exactly = 0) { jenkins.triggerBuild(any()) }
    }

    @Test
    fun `TriggerBuild delegates buildable jobs by full name`() = runTest {
        val jenkins = mockk<JenkinsRepository>()
        val queued = QueuedBuild("android/app", queueItemId = 12, source = DataSource.LIVE)
        coEvery { jenkins.triggerBuild("android/app") } returns Outcome.Success(queued)

        val result = TriggerBuildUseCase(jenkins)(Fixtures.job("android/app"))

        assertEquals(Outcome.Success(queued), result)
        coVerify(exactly = 1) { jenkins.triggerBuild("android/app") }
    }

    @Test
    fun `SaveConnectionSettings rejects an invalid URL in live mode and saves nothing`() = runTest {
        val settings = FakeConnectionSettingsRepository()
        val session = InMemorySessionRepository()

        val result = SaveConnectionSettingsUseCase(settings, session)("http://admin:pw@host/", "dev", DataMode.LIVE, "token-123456")

        assertEquals(Outcome.Failure(JenkinsError.InvalidServerUrl(UrlProblem.ContainsCredentials)), result)
        assertTrue(settings.saved.isEmpty())
        assertNull(session.current())
    }

    @Test
    fun `SaveConnectionSettings normalizes the URL and keeps the token in the session only`() = runTest {
        val settings = FakeConnectionSettingsRepository()
        val session = InMemorySessionRepository()

        val result = SaveConnectionSettingsUseCase(settings, session)(" http://10.0.2.2:8080 ", " dev ", DataMode.LIVE, " token-123456 ")

        val saved = (result as Outcome.Success).value
        assertEquals("http://10.0.2.2:8080/", saved.baseUrl)
        assertEquals("dev", saved.username)
        assertEquals("token-123456", session.current()?.apiToken)
        // ConnectionSettings has no token field at all; nothing secret reaches persistent storage.
        assertTrue(settings.saved.none { it.toString().contains("token-123456") })
    }

    @Test
    fun `SaveConnectionSettings allows an unfinished URL in sample mode`() = runTest {
        val settings = FakeConnectionSettingsRepository()
        val result = SaveConnectionSettingsUseCase(settings, InMemorySessionRepository())("not a url", "", DataMode.SAMPLE, null)
        assertTrue(result is Outcome.Success)
        assertEquals(DataMode.SAMPLE, settings.saved.single().mode)
    }

    @Test
    fun `a blank token keeps the existing session`() = runTest {
        val session = InMemorySessionRepository()
        val save = SaveConnectionSettingsUseCase(FakeConnectionSettingsRepository(), session)
        save("http://10.0.2.2:8080/", "dev", DataMode.LIVE, "first-token-123")
        save("http://10.0.2.2:8080/", "dev", DataMode.LIVE, "")
        assertEquals("first-token-123", session.current()?.apiToken)
    }
}
