package com.example.jenkinslearning.data.repository

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.ConsoleLog
import com.example.jenkinslearning.domain.model.DataMode
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.domain.repository.ConnectionSettingsRepository
import com.example.jenkinslearning.domain.repository.JenkinsRepository
import kotlinx.coroutines.flow.first

/**
 * The [JenkinsRepository] the rest of the app uses. It reads the current
 * [DataMode] on each call and delegates to the sample or live implementation,
 * so switching modes in Settings takes effect on the next refresh.
 */
class ModeAwareJenkinsRepository(
    private val settingsRepository: ConnectionSettingsRepository,
    private val live: JenkinsRepository,
    private val sample: JenkinsRepository,
) : JenkinsRepository {

    private suspend fun current(): JenkinsRepository =
        when (settingsRepository.settings.first().mode) {
            DataMode.LIVE -> live
            DataMode.SAMPLE -> sample
        }

    override suspend fun getServerOverview(): Outcome<ServerOverview> = current().getServerOverview()

    override suspend fun getJobDetails(jobFullName: String): Outcome<JobDetails> =
        current().getJobDetails(jobFullName)

    override suspend fun getBuildDetails(jobFullName: String, buildNumber: Int): Outcome<BuildDetails> =
        current().getBuildDetails(jobFullName, buildNumber)

    override suspend fun getConsoleLog(jobFullName: String, buildNumber: Int): Outcome<ConsoleLog> =
        current().getConsoleLog(jobFullName, buildNumber)

    override suspend fun triggerBuild(jobFullName: String): Outcome<QueuedBuild> =
        current().triggerBuild(jobFullName)
}
