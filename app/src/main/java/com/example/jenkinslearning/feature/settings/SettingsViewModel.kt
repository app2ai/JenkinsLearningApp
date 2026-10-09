package com.example.jenkinslearning.feature.settings

import androidx.lifecycle.viewModelScope
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.AppBuildInfo
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.usecase.ClearSessionUseCase
import com.example.jenkinslearning.domain.usecase.GetDashboardUseCase
import com.example.jenkinslearning.domain.usecase.ObserveConnectionSettingsUseCase
import com.example.jenkinslearning.domain.usecase.ObserveSessionStateUseCase
import com.example.jenkinslearning.domain.usecase.SaveConnectionSettingsUseCase
import com.example.jenkinslearning.presentation.components.ErrorMessages
import com.example.jenkinslearning.presentation.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SettingsViewModel(
    buildInfo: AppBuildInfo,
    observeSettings: ObserveConnectionSettingsUseCase,
    observeSession: ObserveSessionStateUseCase,
    private val saveSettings: SaveConnectionSettingsUseCase,
    private val clearSession: ClearSessionUseCase,
    private val testConnection: GetDashboardUseCase,
) : MviViewModel<SettingsIntent, SettingsState, SettingsEffect>(SettingsState(buildInfo = buildInfo)) {

    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            observeSettings().collect { saved ->
                // Fill the form once; later emissions (caused by our own saves)
                // must not overwrite what the user is typing.
                if (!currentState.formLoaded) {
                    setState { copy(formLoaded = true, baseUrl = saved.baseUrl, username = saved.username, mode = saved.mode) }
                }
            }
        }
        viewModelScope.launch {
            observeSession().collect { has -> setState { copy(hasToken = has) } }
        }
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.BaseUrlChanged -> setState { copy(baseUrl = intent.value, urlError = null, testResult = null) }
            is SettingsIntent.UsernameChanged -> setState { copy(username = intent.value, testResult = null) }
            is SettingsIntent.ModeChanged -> setState { copy(mode = intent.mode, urlError = null, testResult = null) }
            is SettingsIntent.Save -> save(intent.apiToken)
            SettingsIntent.TestConnection -> test()
            SettingsIntent.ClearToken -> {
                clearSession()
                setState { copy(testResult = null) }
                sendEffect(SettingsEffect.ShowMessage("Token removed from memory."))
            }
        }
    }

    private fun save(apiToken: String) {
        if (currentState.isSaving) return
        setState { copy(isSaving = true, urlError = null) }
        viewModelScope.launch {
            val state = currentState
            val result = saveSettings(state.baseUrl, state.username, state.mode, apiToken.takeIf { it.isNotBlank() })
            setState { copy(isSaving = false) }
            when (result) {
                is Outcome.Success -> {
                    setState { copy(baseUrl = result.value.baseUrl, username = result.value.username) }
                    if (apiToken.isNotBlank()) sendEffect(SettingsEffect.ClearTokenField)
                    val tokenNote = when {
                        apiToken.isBlank() -> ""
                        state.username.isBlank() -> " Token ignored: enter a username too."
                        else -> " Token kept in memory only."
                    }
                    sendEffect(SettingsEffect.ShowMessage("Settings saved.$tokenNote"))
                    if (result.value.mode == DataMode.LIVE) test()
                }
                is Outcome.Failure -> {
                    val error = result.error
                    setState {
                        copy(urlError = if (error is JenkinsError.InvalidServerUrl) ErrorMessages.urlProblem(error.reason) else ErrorMessages.title(error))
                    }
                }
            }
        }
    }

    private fun test() {
        testJob?.cancel()
        setState { copy(isTesting = true, testResult = null) }
        testJob = viewModelScope.launch {
            val result = when (val outcome = testConnection()) {
                is Outcome.Success -> ConnectionTestResult.Success(
                    jenkinsVersion = outcome.value.overview.jenkinsVersion,
                    jobCount = outcome.value.jobCount,
                    source = outcome.value.overview.source,
                )
                is Outcome.Failure -> ConnectionTestResult.Failure(outcome.error)
            }
            setState { copy(isTesting = false, testResult = result) }
        }
    }
}
