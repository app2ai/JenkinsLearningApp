package com.example.jenkinslearning.feature.jenkins

import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.JobSummary
import com.example.jenkinslearning.domain.model.QueuedBuild

// ----- Jobs list -----

sealed interface JobsIntent {
    data object Refresh : JobsIntent
    data class OpenJob(val jobFullName: String) : JobsIntent
    data class TriggerBuild(val job: JobSummary) : JobsIntent
}

data class JobsState(
    val isLoading: Boolean = true,
    val jobs: List<JobSummary> = emptyList(),
    val source: DataSource? = null,
    val error: JenkinsError? = null,
    /** Full name of the job whose trigger request is in flight, if any. */
    val triggeringJob: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && jobs.isEmpty()
}

sealed interface JobsEffect {
    data class NavigateToJob(val jobFullName: String) : JobsEffect
    data class ShowMessage(val message: String) : JobsEffect
}

// ----- Job details -----

sealed interface JobDetailsIntent {
    data object Refresh : JobDetailsIntent
    data object TriggerBuild : JobDetailsIntent
    data class OpenBuild(val buildNumber: Int) : JobDetailsIntent
}

data class JobDetailsState(
    val jobFullName: String,
    val isLoading: Boolean = true,
    val details: JobDetails? = null,
    val error: JenkinsError? = null,
    val isTriggering: Boolean = false,
)

sealed interface JobDetailsEffect {
    data class NavigateToBuild(val jobFullName: String, val buildNumber: Int) : JobDetailsEffect
    data class ShowMessage(val message: String) : JobDetailsEffect
}

/** Wording is deliberate: a trigger only queues a build. It doesn't mean it ran or passed. */
fun queuedMessage(queued: QueuedBuild): String = when (queued.source) {
    DataSource.SAMPLE -> "Sample mode: nothing was sent to Jenkins."
    DataSource.LIVE -> buildString {
        append("Jenkins queued a build of ${queued.jobFullName}")
        queued.queueItemId?.let { append(" (queue item #$it)") }
        append(". It hasn't run yet. Refresh to follow it.")
    }
}
