package com.example.jenkinslearning.domain.model

/** A job paired with one of its builds, for "last build" style dashboard rows. */
data class JobBuild(val job: JobSummary, val build: BuildSummary)

/** Buildable jobs plus where they came from, so the Jobs screen can label sample data. */
data class JobList(val jobs: List<JobSummary>, val source: DataSource)

data class DashboardSummary(
    val overview: ServerOverview,
    /** Buildable jobs (folders excluded). */
    val jobCount: Int,
    /** The most recently started build across all jobs. */
    val latestBuild: JobBuild?,
    /** The most recently started successful build across all jobs. */
    val latestSuccessfulBuild: JobBuild?,
)
