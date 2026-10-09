package com.example.jenkinslearning.feature.build

import androidx.lifecycle.viewModelScope
import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.usecase.GetBuildDetailsUseCase
import com.example.jenkinslearning.domain.usecase.GetConsoleLogUseCase
import com.example.jenkinslearning.presentation.components.ErrorMessages
import com.example.jenkinslearning.presentation.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BuildDetailsViewModel(
    jobFullName: String,
    buildNumber: Int,
    private val getBuildDetails: GetBuildDetailsUseCase,
    private val getConsoleLog: GetConsoleLogUseCase,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) : MviViewModel<BuildDetailsIntent, BuildDetailsState, BuildDetailsEffect>(
    BuildDetailsState(jobFullName = jobFullName, buildNumber = buildNumber),
) {
    private var loadJob: Job? = null
    private var consoleJob: Job? = null
    private var pollJob: Job? = null
    private var screenVisible = false

    init {
        load()
    }

    override fun handleIntent(intent: BuildDetailsIntent) {
        when (intent) {
            BuildDetailsIntent.Refresh -> {
                load()
                if (currentState.console is ConsoleState.Loaded) loadConsole()
            }
            BuildDetailsIntent.LoadConsole -> loadConsole()
            is BuildDetailsIntent.OpenArtifact -> openArtifact(intent)
            BuildDetailsIntent.ScreenStarted -> {
                screenVisible = true
                updatePolling()
            }
            BuildDetailsIntent.ScreenStopped -> {
                screenVisible = false
                updatePolling()
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        setState { copy(isLoading = details == null, error = null) }
        loadJob = viewModelScope.launch { fetchDetails() }
    }

    private suspend fun fetchDetails() {
        when (val result = getBuildDetails(currentState.jobFullName, currentState.buildNumber)) {
            is Outcome.Success -> setState { copy(isLoading = false, details = result.value, error = null) }
            is Outcome.Failure -> {
                if (currentState.details == null) {
                    setState { copy(isLoading = false, error = result.error) }
                } else {
                    setState { copy(isLoading = false) }
                    sendEffect(BuildDetailsEffect.ShowMessage(ErrorMessages.title(result.error)))
                }
            }
        }
        updatePolling()
    }

    private fun loadConsole() {
        consoleJob?.cancel()
        setState { copy(console = ConsoleState.Loading) }
        consoleJob = viewModelScope.launch {
            val console = when (val result = getConsoleLog(currentState.jobFullName, currentState.buildNumber)) {
                is Outcome.Success -> ConsoleState.Loaded(result.value)
                is Outcome.Failure -> ConsoleState.Failed(result.error)
            }
            setState { copy(console = console) }
        }
    }

    /** Polls only while the build is running AND the screen is visible. */
    private fun updatePolling() {
        val shouldPoll = screenVisible && currentState.details?.building == true
        if (shouldPoll && pollJob?.isActive != true) {
            pollJob = viewModelScope.launch {
                setState { copy(isPolling = true) }
                try {
                    while (isActive && currentState.details?.building == true) {
                        delay(pollIntervalMillis)
                        getBuildDetails(currentState.jobFullName, currentState.buildNumber).let { result ->
                            if (result is Outcome.Success) setState { copy(details = result.value) }
                        }
                    }
                } finally {
                    setState { copy(isPolling = false) }
                }
            }
        } else if (!shouldPoll) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    private fun openArtifact(intent: BuildDetailsIntent.OpenArtifact) {
        if (currentState.details?.source == DataSource.SAMPLE) {
            sendEffect(BuildDetailsEffect.ShowMessage("Sample artifact: there's no file to download in Sample mode."))
        } else {
            sendEffect(BuildDetailsEffect.OpenUrl(intent.artifact.downloadUrl))
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
    }
}
