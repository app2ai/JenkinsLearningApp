package com.example.jenkinslearning.feature.settings

import app.cash.turbine.test
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.data.local.InMemorySessionRepository
import com.example.jenkinslearning.domain.model.ConnectionSettings
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.usecase.ClearSessionUseCase
import com.example.jenkinslearning.domain.usecase.GetDashboardUseCase
import com.example.jenkinslearning.domain.usecase.ObserveConnectionSettingsUseCase
import com.example.jenkinslearning.domain.usecase.ObserveSessionStateUseCase
import com.example.jenkinslearning.domain.usecase.SaveConnectionSettingsUseCase
import com.example.jenkinslearning.testutil.FakeConnectionSettingsRepository
import com.example.jenkinslearning.testutil.FakeJenkinsRepository
import com.example.jenkinslearning.testutil.Fixtures
import com.example.jenkinslearning.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val token = "11a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7"
    private val settings = FakeConnectionSettingsRepository(ConnectionSettings("http://10.0.2.2:8080/", "dev", DataMode.SAMPLE))
    private val session = InMemorySessionRepository()
    private val jenkins = FakeJenkinsRepository().apply {
        overviewResult = Outcome.Success(Fixtures.overview(listOf(Fixtures.job("app")), version = "2.528.1"))
    }

    private fun viewModel() = SettingsViewModel(
        buildInfo = Fixtures.buildInfo,
        observeSettings = ObserveConnectionSettingsUseCase(settings),
        observeSession = ObserveSessionStateUseCase(session),
        saveSettings = SaveConnectionSettingsUseCase(settings, session),
        clearSession = ClearSessionUseCase(session),
        testConnection = GetDashboardUseCase(jenkins),
    )

    @Test
    fun `form is filled from saved settings once`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.formLoaded)
        assertEquals("dev", vm.state.value.username)

        vm.onIntent(SettingsIntent.UsernameChanged("typing"))
        settings.emit(ConnectionSettings("http://other/", "someone-else", DataMode.SAMPLE))
        advanceUntilIdle()

        assertEquals("typing", vm.state.value.username) // the user's edit isn't overwritten
    }

    @Test
    fun `invalid live URL shows a field error and saves nothing`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.ModeChanged(DataMode.LIVE))
        vm.onIntent(SettingsIntent.BaseUrlChanged("10.0.2.2:8080"))
        vm.onIntent(SettingsIntent.Save(""))
        advanceUntilIdle()

        assertNotNull(vm.state.value.urlError)
        assertTrue(settings.saved.isEmpty())
    }

    @Test
    fun `saving with a token stores it in memory, clears the field, and never puts it in UI state`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(SettingsIntent.ModeChanged(DataMode.LIVE))
            vm.onIntent(SettingsIntent.Save(token))
            advanceUntilIdle()

            assertEquals(SettingsEffect.ClearTokenField, awaitItem())
            assertTrue((awaitItem() as SettingsEffect.ShowMessage).message.contains("memory only"))
        }

        assertEquals(token, session.current()?.apiToken)
        assertTrue(vm.state.value.hasToken)
        assertFalse(vm.state.value.toString().contains(token))
        assertFalse(settings.saved.single().toString().contains(token))
    }

    @Test
    fun `saving in live mode runs a connection test`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.ModeChanged(DataMode.LIVE))
        vm.onIntent(SettingsIntent.Save(token))
        advanceUntilIdle()

        val result = vm.state.value.testResult as ConnectionTestResult.Success
        assertEquals("2.528.1", result.jenkinsVersion)
        assertEquals(1, result.jobCount)
        assertFalse(vm.state.value.isTesting)
    }

    @Test
    fun `forget token clears the session`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.Save(token))
        advanceUntilIdle()
        assertTrue(vm.state.value.hasToken)

        vm.onIntent(SettingsIntent.ClearToken)
        advanceUntilIdle()

        assertNull(session.current())
        assertFalse(vm.state.value.hasToken)
    }

    @Test
    fun `http URLs are flagged as cleartext`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.isCleartextUrl)
        vm.onIntent(SettingsIntent.BaseUrlChanged("https://jenkins.example.com/"))
        assertFalse(vm.state.value.isCleartextUrl)
    }
}
