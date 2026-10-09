package com.example.jenkinslearning.domain.repository

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.ConfigConcept
import com.example.jenkinslearning.domain.model.ConnectionSettings
import com.example.jenkinslearning.domain.model.ConsoleLog
import com.example.jenkinslearning.domain.model.CredentialGuideEntry
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.LearningTopic
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.domain.model.SessionCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Read and trigger operations against Jenkins. Implemented by the live REST
 * client, the sample-data repository, and a mode-aware wrapper that picks one
 * of them based on [ConnectionSettings.mode].
 */
interface JenkinsRepository {
    suspend fun getServerOverview(): Outcome<ServerOverview>
    suspend fun getJobDetails(jobFullName: String): Outcome<JobDetails>
    suspend fun getBuildDetails(jobFullName: String, buildNumber: Int): Outcome<BuildDetails>
    suspend fun getConsoleLog(jobFullName: String, buildNumber: Int): Outcome<ConsoleLog>
    suspend fun triggerBuild(jobFullName: String): Outcome<QueuedBuild>
}

/** Persisted, non-secret settings (server URL, username, data mode). */
interface ConnectionSettingsRepository {
    val settings: Flow<ConnectionSettings>
    suspend fun save(settings: ConnectionSettings)
}

/**
 * The API token for the current process. Kept in memory only: it's never
 * written to DataStore, SharedPreferences, files or saved instance state.
 * See docs/04-credentials-and-secrets.md for the Keystore-backed alternative.
 */
interface SessionRepository {
    val hasCredentials: StateFlow<Boolean>
    fun current(): SessionCredentials?
    fun set(credentials: SessionCredentials)
    fun clear()
}

/** Static educational content shown in the Credentials and Learning screens. */
interface LearningContentRepository {
    fun credentialGuide(): List<CredentialGuideEntry>
    fun configConcepts(): List<ConfigConcept>
    fun learningTopics(): List<LearningTopic>
}
