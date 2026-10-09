package com.example.jenkinslearning.feature.settings

import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.AppBuildInfo
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.DataSource

sealed interface SettingsIntent {
    data class BaseUrlChanged(val value: String) : SettingsIntent
    data class UsernameChanged(val value: String) : SettingsIntent
    data class ModeChanged(val mode: DataMode) : SettingsIntent

    /**
     * Carries the token typed by the user, once, from the UI to the ViewModel.
     * Not a data class: toString() must never print the token (intents are
     * often logged while debugging MVI flows).
     */
    class Save(val apiToken: String) : SettingsIntent {
        override fun toString(): String = "Save(apiToken=****)"
    }

    data object TestConnection : SettingsIntent
    data object ClearToken : SettingsIntent
}

sealed interface ConnectionTestResult {
    data class Success(val jenkinsVersion: String?, val jobCount: Int, val source: DataSource) : ConnectionTestResult
    data class Failure(val error: JenkinsError) : ConnectionTestResult
}

/**
 * Note what's NOT here: the API token. The UI state only knows whether a
 * token is set ([hasToken]); the token itself lives in the in-memory session.
 */
data class SettingsState(
    val buildInfo: AppBuildInfo,
    val formLoaded: Boolean = false,
    val baseUrl: String = "",
    val username: String = "",
    val mode: DataMode = DataMode.SAMPLE,
    val hasToken: Boolean = false,
    val urlError: String? = null,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
) {
    val isCleartextUrl: Boolean get() = baseUrl.trim().startsWith("http://", ignoreCase = true)
}

sealed interface SettingsEffect {
    data class ShowMessage(val message: String) : SettingsEffect

    /** The token was handed to the session; the screen should clear its text field. */
    data object ClearTokenField : SettingsEffect
}
