package com.example.jenkinslearning.feature.dashboard

import app.cash.turbine.test
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.ConnectionSettings
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.usecase.GetDashboardUseCase
import com.example.jenkinslearning.domain.usecase.ObserveConnectionSettingsUseCase
import com.example.jenkinslearning.testutil.FakeConnectionSettingsRepository
import com.example.jenkinslearning.testutil.FakeJenkinsRepository
import com.example.jenkinslearning.testutil.Fixtures
import com.example.jenkinslearning.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeJenkinsRepository()
    private val settings = FakeConnectionSettingsRepository()

    private fun viewModel() = DashboardViewModel(GetDashboardUseCase(repository), ObserveConnectionSettingsUseCase(settings))

    @Test
    fun `starts loading, then shows the summary`() = runTest(mainRule.dispatcher) {
        repository.overviewResult = Outcome.Success(Fixtures.overview(listOf(Fixtures.job("app"))))
        val vm = viewModel()

        assertTrue(vm.state.value.isLoading)
        assertEquals(ConnectionStatus.CONNECTING, vm.state.value.connection)

        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.isLoading)
        assertEquals(1, state.summary?.jobCount)
        assertEquals(ConnectionStatus.CONNECTED, state.connection)
        assertNull(state.error)
    }

    @Test
    fun `no jobs is an empty state, not an error`() = runTest(mainRule.dispatcher) {
        repository.overviewResult = Outcome.Success(Fixtures.overview(emptyList()))
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.isEmpty)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `sample data is reported as sample mode`() = runTest(mainRule.dispatcher) {
        repository.overviewResult = Outcome.Success(Fixtures.overview(listOf(Fixtures.job("app")), source = DataSource.SAMPLE))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ConnectionStatus.SAMPLE, vm.state.value.connection)
    }

    @Test
    fun `initial failure shows the error state`() = runTest(mainRule.dispatcher) {
        repository.overviewResult = Outcome.Failure(JenkinsError.HostUnreachable)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(JenkinsError.HostUnreachable, vm.state.value.error)
        assertNull(vm.state.value.summary)
        assertEquals(ConnectionStatus.FAILED, vm.state.value.connection)
    }

    @Test
    fun `refresh failure keeps the data and shows a one-time message`() = runTest(mainRule.dispatcher) {
        repository.overviewResult = Outcome.Success(Fixtures.overview(listOf(Fixtures.job("app"))))
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            repository.overviewResult = Outcome.Failure(JenkinsError.Timeout)
            vm.onIntent(DashboardIntent.Refresh)
            assertTrue(vm.state.value.isRefreshing)
            advanceUntilIdle()

            assertEquals(DashboardEffect.ShowMessage("Request timed out"), awaitItem())
            assertNotNull(vm.state.value.summary)
            assertNull(vm.state.value.error)
        }
    }

    @Test
    fun `changing settings reloads automatically`() = runTest(mainRule.dispatcher) {
        repository.overviewResult = Outcome.Success(Fixtures.overview(listOf(Fixtures.job("app"))))
        viewModel()
        advanceUntilIdle()
        assertEquals(1, repository.overviewCalls)

        settings.emit(ConnectionSettings("http://10.0.2.2:8080/", "dev", DataMode.SAMPLE))
        advanceUntilIdle()

        assertEquals(2, repository.overviewCalls)
    }

    @Test
    fun `navigation intents become effects`() = runTest(mainRule.dispatcher) {
        repository.overviewResult = Outcome.Success(Fixtures.overview(emptyList()))
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(DashboardIntent.OpenJob("android/app"))
            assertEquals(DashboardEffect.NavigateToJob("android/app"), awaitItem())
            vm.onIntent(DashboardIntent.OpenSettings)
            assertEquals(DashboardEffect.NavigateToSettings, awaitItem())
        }
    }
}
