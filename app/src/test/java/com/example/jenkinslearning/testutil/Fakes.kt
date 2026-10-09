package com.example.jenkinslearning.testutil

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.ConnectionSettings
import com.example.jenkinslearning.domain.model.ConsoleLog
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.domain.repository.ConnectionSettingsRepository
import com.example.jenkinslearning.domain.repository.JenkinsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Deterministic in-memory [JenkinsRepository] for ViewModel and use case tests.
 * Each result is configurable; every call is counted.
 */
class FakeJenkinsRepository : JenkinsRepository {
    var overviewResult: Outcome<ServerOverview> = Outcome.Failure(JenkinsError.NotConfigured)
    var jobDetailsResult: Outcome<JobDetails> = Outcome.Failure(JenkinsError.NotFound)
    var buildDetailsResult: Outcome<BuildDetails> = Outcome.Failure(JenkinsError.NotFound)
    var consoleResult: Outcome<ConsoleLog> = Outcome.Failure(JenkinsError.NotFound)
    var triggerResult: Outcome<QueuedBuild> = Outcome.Failure(JenkinsError.Forbidden)

    /** Optional suspension point so tests can observe in-flight states. */
    var beforeEachCall: suspend () -> Unit = {}

    var overviewCalls = 0
    var jobDetailsCalls = 0
    var buildDetailsCalls = 0
    var consoleCalls = 0
    val triggeredJobs = mutableListOf<String>()

    override suspend fun getServerOverview(): Outcome<ServerOverview> {
        overviewCalls++
        beforeEachCall()
        return overviewResult
    }

    override suspend fun getJobDetails(jobFullName: String): Outcome<JobDetails> {
        jobDetailsCalls++
        beforeEachCall()
        return jobDetailsResult
    }

    override suspend fun getBuildDetails(jobFullName: String, buildNumber: Int): Outcome<BuildDetails> {
        buildDetailsCalls++
        beforeEachCall()
        return buildDetailsResult
    }

    override suspend fun getConsoleLog(jobFullName: String, buildNumber: Int): Outcome<ConsoleLog> {
        consoleCalls++
        beforeEachCall()
        return consoleResult
    }

    override suspend fun triggerBuild(jobFullName: String): Outcome<QueuedBuild> {
        triggeredJobs += jobFullName
        beforeEachCall()
        return triggerResult
    }
}

class FakeConnectionSettingsRepository(
    initial: ConnectionSettings = ConnectionSettings("http://10.0.2.2:8080/", "dev", DataMode.LIVE),
) : ConnectionSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<ConnectionSettings> = state
    val saved = mutableListOf<ConnectionSettings>()

    override suspend fun save(settings: ConnectionSettings) {
        saved += settings
        state.value = settings
    }

    fun emit(settings: ConnectionSettings) {
        state.value = settings
    }
}
