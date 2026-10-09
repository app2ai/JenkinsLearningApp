package com.example.jenkinslearning.domain.usecase

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.core.common.map
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.BuildSummary
import com.example.jenkinslearning.domain.model.ConsoleLog
import com.example.jenkinslearning.domain.model.DashboardSummary
import com.example.jenkinslearning.domain.model.JobBuild
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.JobList
import com.example.jenkinslearning.domain.model.JobSummary
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.domain.repository.JenkinsRepository

/** Builds the dashboard: connection info plus the latest build and latest successful build across jobs. */
class GetDashboardUseCase(private val repository: JenkinsRepository) {

    suspend operator fun invoke(): Outcome<DashboardSummary> =
        repository.getServerOverview().map(::summarize)

    internal fun summarize(overview: ServerOverview): DashboardSummary {
        val jobs = overview.jobs.filterNot { it.isFolder }
        return DashboardSummary(
            overview = overview,
            jobCount = jobs.size,
            latestBuild = jobs.latestBy { it.lastBuild },
            latestSuccessfulBuild = jobs.latestBy { it.lastSuccessfulBuild },
        )
    }

    private fun List<JobSummary>.latestBy(select: (JobSummary) -> BuildSummary?): JobBuild? =
        mapNotNull { job -> select(job)?.let { JobBuild(job, it) } }
            // Builds without a timestamp sort last; ties fall back to build number.
            .maxWithOrNull(compareBy<JobBuild>({ it.build.startedAtMillis ?: Long.MIN_VALUE }, { it.build.number }))
}

/** Lists buildable jobs (folders flattened away), sorted by name. */
class GetJobsUseCase(private val repository: JenkinsRepository) {
    suspend operator fun invoke(): Outcome<JobList> =
        repository.getServerOverview().map { overview ->
            JobList(
                jobs = overview.jobs.filterNot { it.isFolder }.sortedBy { it.fullName.lowercase() },
                source = overview.source,
            )
        }
}

class GetJobDetailsUseCase(private val repository: JenkinsRepository) {
    suspend operator fun invoke(jobFullName: String): Outcome<JobDetails> {
        if (jobFullName.isBlank()) return Outcome.Failure(JenkinsError.NotFound)
        return repository.getJobDetails(jobFullName)
    }
}

class GetBuildDetailsUseCase(private val repository: JenkinsRepository) {
    suspend operator fun invoke(jobFullName: String, buildNumber: Int): Outcome<BuildDetails> {
        if (jobFullName.isBlank() || buildNumber <= 0) return Outcome.Failure(JenkinsError.NotFound)
        return repository.getBuildDetails(jobFullName, buildNumber)
    }
}

class GetConsoleLogUseCase(private val repository: JenkinsRepository) {
    suspend operator fun invoke(jobFullName: String, buildNumber: Int): Outcome<ConsoleLog> {
        if (jobFullName.isBlank() || buildNumber <= 0) return Outcome.Failure(JenkinsError.NotFound)
        return repository.getConsoleLog(jobFullName, buildNumber)
    }
}

/**
 * Asks Jenkins to queue a build. Success means "queued", not "started" or
 * "passed". The UI must say exactly that.
 */
class TriggerBuildUseCase(private val repository: JenkinsRepository) {
    suspend operator fun invoke(job: JobSummary): Outcome<QueuedBuild> {
        if (job.isFolder || !job.buildable) return Outcome.Failure(JenkinsError.JobNotBuildable)
        return repository.triggerBuild(job.fullName)
    }

    suspend operator fun invoke(details: JobDetails): Outcome<QueuedBuild> {
        if (!details.buildable) return Outcome.Failure(JenkinsError.JobNotBuildable)
        return repository.triggerBuild(details.fullName)
    }
}
