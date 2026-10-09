package com.example.jenkinslearning.feature.build

import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.BuildArtifact
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.ConsoleLog

sealed interface BuildDetailsIntent {
    data object Refresh : BuildDetailsIntent
    data object LoadConsole : BuildDetailsIntent
    data class OpenArtifact(val artifact: BuildArtifact) : BuildDetailsIntent

    /** Sent by the screen's lifecycle so polling only runs while the screen is visible. */
    data object ScreenStarted : BuildDetailsIntent
    data object ScreenStopped : BuildDetailsIntent
}

sealed interface ConsoleState {
    data object NotLoaded : ConsoleState
    data object Loading : ConsoleState
    data class Loaded(val log: ConsoleLog) : ConsoleState
    data class Failed(val error: JenkinsError) : ConsoleState
}

data class BuildDetailsState(
    val jobFullName: String,
    val buildNumber: Int,
    val isLoading: Boolean = true,
    val details: BuildDetails? = null,
    val error: JenkinsError? = null,
    val console: ConsoleState = ConsoleState.NotLoaded,
    /** True while the screen auto-refreshes a running build. */
    val isPolling: Boolean = false,
)

sealed interface BuildDetailsEffect {
    data class OpenUrl(val url: String) : BuildDetailsEffect
    data class ShowMessage(val message: String) : BuildDetailsEffect
}
