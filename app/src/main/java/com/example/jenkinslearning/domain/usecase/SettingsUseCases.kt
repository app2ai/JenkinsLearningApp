package com.example.jenkinslearning.domain.usecase

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.core.config.JenkinsUrlParser
import com.example.jenkinslearning.core.config.UrlParseResult
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.ConnectionSettings
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.SessionCredentials
import com.example.jenkinslearning.domain.repository.ConnectionSettingsRepository
import com.example.jenkinslearning.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ObserveConnectionSettingsUseCase(private val repository: ConnectionSettingsRepository) {
    operator fun invoke(): Flow<ConnectionSettings> = repository.settings
}

/**
 * Validates and saves connection settings.
 *
 * - URL, username and mode go to persistent storage (they aren't secrets).
 * - The API token, if provided, goes to the in-memory session only.
 * - A blank token leaves the current session token unchanged.
 */
class SaveConnectionSettingsUseCase(
    private val settingsRepository: ConnectionSettingsRepository,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(
        baseUrl: String,
        username: String,
        mode: DataMode,
        apiToken: String?,
    ): Outcome<ConnectionSettings> {
        val trimmedUser = username.trim()
        val parsed = JenkinsUrlParser.parse(baseUrl)

        // Sample mode never contacts a server, so a bad URL is allowed to be saved there.
        val normalizedUrl = when (parsed) {
            is UrlParseResult.Valid -> parsed.baseUrl
            is UrlParseResult.Invalid ->
                if (mode == DataMode.LIVE) return Outcome.Failure(JenkinsError.InvalidServerUrl(parsed.problem)) else baseUrl.trim()
        }

        val settings = ConnectionSettings(baseUrl = normalizedUrl, username = trimmedUser, mode = mode)
        settingsRepository.save(settings)

        if (!apiToken.isNullOrBlank() && trimmedUser.isNotEmpty()) {
            sessionRepository.set(SessionCredentials(trimmedUser, apiToken.trim()))
        }
        return Outcome.Success(settings)
    }
}

class ClearSessionUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke() = sessionRepository.clear()
}

/** Exposes only whether a token is set, never the token itself. */
class ObserveSessionStateUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(): StateFlow<Boolean> = sessionRepository.hasCredentials
}
