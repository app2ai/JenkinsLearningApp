package com.example.jenkinslearning.domain.model

/**
 * Where a piece of data came from. The UI labels every [SAMPLE] screen so
 * built-in sample data is never mistaken for a real Jenkins response.
 */
enum class DataSource { LIVE, SAMPLE }

enum class BuildStatus {
    SUCCESS,
    UNSTABLE,
    FAILURE,
    ABORTED,
    NOT_BUILT,
    DISABLED,
    IN_PROGRESS,
    UNKNOWN,
}

data class BuildSummary(
    val number: Int,
    val status: BuildStatus,
    /** Start time in epoch millis, or null if Jenkins didn't report it. */
    val startedAtMillis: Long?,
    /** Duration in millis. Jenkins reports 0 while a build is running. */
    val durationMillis: Long?,
)

data class JobSummary(
    val name: String,
    /** Folder-qualified name, e.g. "android/JenkinsLearningApp". Used to build URLs. */
    val fullName: String,
    val status: BuildStatus,
    val lastBuild: BuildSummary?,
    val lastSuccessfulBuild: BuildSummary?,
    val isFolder: Boolean,
    val buildable: Boolean,
)

data class ServerOverview(
    val baseUrl: String,
    /** From the X-Jenkins response header; null if Jenkins hides it. */
    val jenkinsVersion: String?,
    val jobs: List<JobSummary>,
    val source: DataSource,
)

data class JobDetails(
    val name: String,
    val fullName: String,
    val description: String?,
    val status: BuildStatus,
    val buildable: Boolean,
    val inQueue: Boolean,
    val isParameterized: Boolean,
    val builds: List<BuildSummary>,
    val lastSuccessfulBuildNumber: Int?,
    val lastFailedBuildNumber: Int?,
    val source: DataSource,
)

data class TestSummary(
    val total: Int,
    val failed: Int,
    val skipped: Int,
) {
    val passed: Int get() = (total - failed - skipped).coerceAtLeast(0)
}

data class BuildArtifact(
    val fileName: String,
    val relativePath: String,
    /** Absolute download URL. Opening it in a browser needs a Jenkins login. */
    val downloadUrl: String,
)

data class BuildDetails(
    val jobFullName: String,
    val number: Int,
    val displayName: String?,
    val status: BuildStatus,
    val building: Boolean,
    val startedAtMillis: Long?,
    val durationMillis: Long?,
    val estimatedDurationMillis: Long?,
    val artifacts: List<BuildArtifact>,
    /** Null when the build didn't publish JUnit results. */
    val tests: TestSummary?,
    val source: DataSource,
)

data class ConsoleLog(
    /** Console text with known secret patterns already replaced by "****". */
    val text: String,
    /** True if only the tail of a long log was kept. */
    val truncated: Boolean,
    /** How many values the app masked on top of Jenkins' own masking. */
    val redactions: Int,
    val source: DataSource,
)

/**
 * Jenkins answered 201 Created and put the build in its queue. That's all a
 * trigger guarantees: it doesn't mean the build started or succeeded.
 */
data class QueuedBuild(
    val jobFullName: String,
    /** Queue item id parsed from the Location header, when available. */
    val queueItemId: Long?,
    val source: DataSource,
)
