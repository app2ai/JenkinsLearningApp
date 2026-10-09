package com.example.jenkinslearning.data.repository

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.ConnectionSettings
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.testutil.FakeConnectionSettingsRepository
import com.example.jenkinslearning.testutil.FakeJenkinsRepository
import com.example.jenkinslearning.testutil.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeAndSampleRepositoryTest {

    @Test
    fun `mode-aware repository follows the current mode on every call`() = runTest {
        val settings = FakeConnectionSettingsRepository(ConnectionSettings("http://h/", "", DataMode.SAMPLE))
        val live = FakeJenkinsRepository().apply { overviewResult = Outcome.Success(Fixtures.overview(emptyList())) }
        val sample = FakeJenkinsRepository().apply {
            overviewResult = Outcome.Success(Fixtures.overview(emptyList(), source = DataSource.SAMPLE))
        }
        val repository = ModeAwareJenkinsRepository(settings, live, sample)

        repository.getServerOverview()
        settings.emit(ConnectionSettings("http://h/", "", DataMode.LIVE))
        repository.getServerOverview()

        assertEquals(1, sample.overviewCalls)
        assertEquals(1, live.overviewCalls)
    }

    private val sampleRepository = SampleJenkinsRepository(latencyMillis = 0, now = { 10_000_000_000L })

    @Test
    fun `every sample result is labeled as sample data`() = runTest {
        val overview = (sampleRepository.getServerOverview() as Outcome.Success).value
        assertEquals(DataSource.SAMPLE, overview.source)

        val job = overview.jobs.first { it.buildable }
        assertEquals(DataSource.SAMPLE, (sampleRepository.getJobDetails(job.fullName) as Outcome.Success).value.source)
        val build = job.lastBuild!!.number
        assertEquals(DataSource.SAMPLE, (sampleRepository.getBuildDetails(job.fullName, build) as Outcome.Success).value.source)
        assertEquals(DataSource.SAMPLE, (sampleRepository.triggerBuild(job.fullName) as Outcome.Success).value.source)
    }

    @Test
    fun `sample console says it is sample output and masks the leaked password`() = runTest {
        val log = (sampleRepository.getConsoleLog("JenkinsLearningApp", 42) as Outcome.Success).value
        assertTrue(log.text.startsWith("SAMPLE CONSOLE OUTPUT"))
        assertFalse(log.text.contains("hunter2"))
        assertTrue(log.redactions >= 1)
    }

    @Test
    fun `sample repository refuses disabled jobs and unknown names`() = runTest {
        assertEquals(Outcome.Failure(JenkinsError.JobNotBuildable), sampleRepository.triggerBuild("release-signing"))
        assertEquals(Outcome.Failure(JenkinsError.NotFound), sampleRepository.getJobDetails("does-not-exist"))
    }
}
