package com.example.jenkinslearning.feature.build

import app.cash.turbine.test
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.BuildArtifact
import com.example.jenkinslearning.domain.model.ConsoleLog
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.usecase.GetBuildDetailsUseCase
import com.example.jenkinslearning.domain.usecase.GetConsoleLogUseCase
import com.example.jenkinslearning.testutil.FakeJenkinsRepository
import com.example.jenkinslearning.testutil.Fixtures
import com.example.jenkinslearning.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BuildDetailsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeJenkinsRepository()
    private val interval = 5_000L

    private fun viewModel() = BuildDetailsViewModel(
        jobFullName = "app",
        buildNumber = 7,
        getBuildDetails = GetBuildDetailsUseCase(repository),
        getConsoleLog = GetConsoleLogUseCase(repository),
        pollIntervalMillis = interval,
    )

    @Test
    fun `loads details, console loads only on request`() = runTest(mainRule.dispatcher) {
        repository.buildDetailsResult = Outcome.Success(Fixtures.buildDetails())
        repository.consoleResult = Outcome.Success(ConsoleLog("log", truncated = false, redactions = 1, source = DataSource.LIVE))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(7, vm.state.value.details?.number)
        assertEquals(ConsoleState.NotLoaded, vm.state.value.console)
        assertEquals(0, repository.consoleCalls)

        vm.onIntent(BuildDetailsIntent.LoadConsole)
        assertEquals(ConsoleState.Loading, vm.state.value.console)
        advanceUntilIdle()

        val loaded = vm.state.value.console as ConsoleState.Loaded
        assertEquals(1, loaded.log.redactions)
    }

    @Test
    fun `console authorization failure is shown in the console section only`() = runTest(mainRule.dispatcher) {
        repository.buildDetailsResult = Outcome.Success(Fixtures.buildDetails())
        repository.consoleResult = Outcome.Failure(JenkinsError.Forbidden)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(BuildDetailsIntent.LoadConsole)
        advanceUntilIdle()

        assertEquals(ConsoleState.Failed(JenkinsError.Forbidden), vm.state.value.console)
        assertEquals(7, vm.state.value.details?.number)
    }

    @Test
    fun `polls a running build only while the screen is visible`() = runTest(mainRule.dispatcher) {
        repository.buildDetailsResult = Outcome.Success(Fixtures.buildDetails(building = true))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1, repository.buildDetailsCalls)

        // Not visible yet: no polling.
        advanceTimeBy(interval * 3)
        runCurrent()
        assertEquals(1, repository.buildDetailsCalls)

        vm.onIntent(BuildDetailsIntent.ScreenStarted)
        runCurrent()
        assertTrue(vm.state.value.isPolling)
        advanceTimeBy(interval + 1)
        runCurrent()
        assertEquals(2, repository.buildDetailsCalls)

        vm.onIntent(BuildDetailsIntent.ScreenStopped)
        advanceTimeBy(interval * 3)
        runCurrent()
        assertEquals(2, repository.buildDetailsCalls)
        assertFalse(vm.state.value.isPolling)
    }

    @Test
    fun `polling stops when the build finishes`() = runTest(mainRule.dispatcher) {
        repository.buildDetailsResult = Outcome.Success(Fixtures.buildDetails(building = true))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BuildDetailsIntent.ScreenStarted)
        runCurrent()

        repository.buildDetailsResult = Outcome.Success(Fixtures.buildDetails(building = false))
        advanceTimeBy(interval + 1)
        runCurrent()

        assertFalse(vm.state.value.details!!.building)
        assertFalse(vm.state.value.isPolling)
        val callsAfterFinish = repository.buildDetailsCalls
        advanceTimeBy(interval * 3)
        runCurrent()
        assertEquals(callsAfterFinish, repository.buildDetailsCalls)
        vm.onIntent(BuildDetailsIntent.ScreenStopped)
    }

    @Test
    fun `artifacts open in the browser for live builds but not for sample builds`() = runTest(mainRule.dispatcher) {
        val artifact = BuildArtifact("app-debug.apk", "app/app-debug.apk", "http://10.0.2.2:8080/job/app/7/artifact/app/app-debug.apk")

        repository.buildDetailsResult = Outcome.Success(Fixtures.buildDetails(source = DataSource.LIVE))
        val live = viewModel()
        advanceUntilIdle()
        live.effects.test {
            live.onIntent(BuildDetailsIntent.OpenArtifact(artifact))
            assertEquals(BuildDetailsEffect.OpenUrl(artifact.downloadUrl), awaitItem())
        }

        repository.buildDetailsResult = Outcome.Success(Fixtures.buildDetails(source = DataSource.SAMPLE))
        val sample = viewModel()
        advanceUntilIdle()
        sample.effects.test {
            sample.onIntent(BuildDetailsIntent.OpenArtifact(artifact))
            assertTrue(awaitItem() is BuildDetailsEffect.ShowMessage)
        }
    }

    @Test
    fun `initial failure shows the error state`() = runTest(mainRule.dispatcher) {
        repository.buildDetailsResult = Outcome.Failure(JenkinsError.NotFound)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(JenkinsError.NotFound, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }
}
