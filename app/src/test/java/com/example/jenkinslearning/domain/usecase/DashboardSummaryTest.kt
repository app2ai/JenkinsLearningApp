package com.example.jenkinslearning.domain.usecase

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.BuildStatus
import com.example.jenkinslearning.domain.model.ConsoleLog
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.domain.repository.JenkinsRepository
import com.example.jenkinslearning.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure tests of the dashboard aggregation (no coroutines needed). */
class DashboardSummaryTest {

    private val unusedRepository = object : JenkinsRepository {
        override suspend fun getServerOverview(): Outcome<ServerOverview> = error("unused")
        override suspend fun getJobDetails(jobFullName: String): Outcome<JobDetails> = error("unused")
        override suspend fun getBuildDetails(jobFullName: String, buildNumber: Int): Outcome<BuildDetails> = error("unused")
        override suspend fun getConsoleLog(jobFullName: String, buildNumber: Int): Outcome<ConsoleLog> = error("unused")
        override suspend fun triggerBuild(jobFullName: String): Outcome<QueuedBuild> = error("unused")
    }
    private val useCase = GetDashboardUseCase(unusedRepository)

    @Test
    fun `picks the most recently started build and successful build across jobs`() {
        val overview = Fixtures.overview(
            listOf(
                Fixtures.job("a", lastBuild = Fixtures.build(5, startedAt = 100), lastSuccessful = Fixtures.build(5, startedAt = 100)),
                Fixtures.job(
                    "b",
                    status = BuildStatus.FAILURE,
                    lastBuild = Fixtures.build(9, BuildStatus.FAILURE, startedAt = 300),
                    lastSuccessful = Fixtures.build(7, startedAt = 50),
                ),
                Fixtures.job("folder", isFolder = true, lastBuild = Fixtures.build(99, startedAt = 999)),
            ),
        )

        val summary = useCase.summarize(overview)

        assertEquals(2, summary.jobCount) // folder excluded
        assertEquals("b", summary.latestBuild?.job?.fullName)
        assertEquals(9, summary.latestBuild?.build?.number)
        assertEquals("a", summary.latestSuccessfulBuild?.job?.fullName)
    }

    @Test
    fun `builds without timestamps sort last`() {
        val overview = Fixtures.overview(
            listOf(
                Fixtures.job("no-time", lastBuild = Fixtures.build(50, startedAt = null)),
                Fixtures.job("timed", lastBuild = Fixtures.build(1, startedAt = 10)),
            ),
        )
        assertEquals("timed", useCase.summarize(overview).latestBuild?.job?.fullName)
    }

    @Test
    fun `empty server has no latest builds`() {
        val summary = useCase.summarize(Fixtures.overview(emptyList()))
        assertEquals(0, summary.jobCount)
        assertNull(summary.latestBuild)
        assertNull(summary.latestSuccessfulBuild)
    }
}
