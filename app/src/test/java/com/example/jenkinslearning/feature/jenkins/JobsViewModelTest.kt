package com.example.jenkinslearning.feature.jenkins

import app.cash.turbine.test
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.usecase.GetJobDetailsUseCase
import com.example.jenkinslearning.domain.usecase.GetJobsUseCase
import com.example.jenkinslearning.domain.usecase.ObserveConnectionSettingsUseCase
import com.example.jenkinslearning.domain.usecase.TriggerBuildUseCase
import com.example.jenkinslearning.testutil.FakeConnectionSettingsRepository
import com.example.jenkinslearning.testutil.FakeJenkinsRepository
import com.example.jenkinslearning.testutil.Fixtures
import com.example.jenkinslearning.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JobsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeJenkinsRepository().apply {
        overviewResult = Outcome.Success(Fixtures.overview(listOf(Fixtures.job("app"), Fixtures.job("lib"))))
    }

    private fun jobsViewModel() = JobsViewModel(
        GetJobsUseCase(repository),
        TriggerBuildUseCase(repository),
        ObserveConnectionSettingsUseCase(FakeConnectionSettingsRepository()),
    )

    @Test
    fun `lists jobs with their data source`() = runTest(mainRule.dispatcher) {
        val vm = jobsViewModel()
        advanceUntilIdle()
        assertEquals(listOf("app", "lib"), vm.state.value.jobs.map { it.fullName })
        assertEquals(DataSource.LIVE, vm.state.value.source)
        assertFalse(vm.state.value.isEmpty)
    }

    @Test
    fun `load failure shows an error`() = runTest(mainRule.dispatcher) {
        repository.overviewResult = Outcome.Failure(JenkinsError.Forbidden)
        val vm = jobsViewModel()
        advanceUntilIdle()
        assertEquals(JenkinsError.Forbidden, vm.state.value.error)
        assertTrue(vm.state.value.jobs.isEmpty())
    }

    @Test
    fun `a successful trigger says queued, not succeeded, then reloads`() = runTest(mainRule.dispatcher) {
        repository.triggerResult = Outcome.Success(QueuedBuild("app", queueItemId = 77, source = DataSource.LIVE))
        val vm = jobsViewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(JobsIntent.TriggerBuild(Fixtures.job("app")))
            advanceUntilIdle()
            val message = (awaitItem() as JobsEffect.ShowMessage).message
            assertTrue(message.contains("queued"))
            assertTrue(message.contains("#77"))
            assertTrue(message.contains("hasn't run yet"))
            assertFalse(message.contains("succe", ignoreCase = true))
        }
        assertEquals(listOf("app"), repository.triggeredJobs)
        assertEquals(2, repository.overviewCalls)
        assertNull(vm.state.value.triggeringJob)
    }

    @Test
    fun `a forbidden trigger is reported as an error`() = runTest(mainRule.dispatcher) {
        repository.triggerResult = Outcome.Failure(JenkinsError.Forbidden)
        val vm = jobsViewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(JobsIntent.TriggerBuild(Fixtures.job("app")))
            advanceUntilIdle()
            assertTrue((awaitItem() as JobsEffect.ShowMessage).message.startsWith("Permission denied (403)"))
        }
    }

    @Test
    fun `double taps while a trigger is in flight are ignored`() = runTest(mainRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.triggerResult = Outcome.Success(QueuedBuild("app", null, DataSource.LIVE))
        val vm = jobsViewModel()
        advanceUntilIdle()

        repository.beforeEachCall = { gate.await() }
        vm.onIntent(JobsIntent.TriggerBuild(Fixtures.job("app")))
        vm.onIntent(JobsIntent.TriggerBuild(Fixtures.job("app")))
        advanceUntilIdle()
        assertEquals("app", vm.state.value.triggeringJob)

        repository.beforeEachCall = {}
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.triggeredJobs.size)
    }

    @Test
    fun `sample triggers say nothing was sent`() = runTest(mainRule.dispatcher) {
        assertEquals(
            "Sample mode: nothing was sent to Jenkins.",
            queuedMessage(QueuedBuild("app", null, DataSource.SAMPLE)),
        )
    }

    @Test
    fun `job details trigger refuses a non-buildable job without calling Jenkins`() = runTest(mainRule.dispatcher) {
        repository.jobDetailsResult = Outcome.Success(Fixtures.jobDetails(buildable = false))
        val vm = JobDetailsViewModel("app", GetJobDetailsUseCase(repository), TriggerBuildUseCase(repository))
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(JobDetailsIntent.TriggerBuild)
            advanceUntilIdle()
            assertTrue((awaitItem() as JobDetailsEffect.ShowMessage).message.startsWith("This job can't be built"))
        }
        assertTrue(repository.triggeredJobs.isEmpty())
    }

    @Test
    fun `opening a build navigates with the job name and number`() = runTest(mainRule.dispatcher) {
        repository.jobDetailsResult = Outcome.Success(Fixtures.jobDetails("android/app"))
        val vm = JobDetailsViewModel("android/app", GetJobDetailsUseCase(repository), TriggerBuildUseCase(repository))
        vm.effects.test {
            vm.onIntent(JobDetailsIntent.OpenBuild(2))
            assertEquals(JobDetailsEffect.NavigateToBuild("android/app", 2), awaitItem())
        }
    }
}
