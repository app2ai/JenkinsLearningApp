package com.example.jenkinslearning.data.repository

import com.example.jenkinslearning.core.common.Outcome
import com.example.jenkinslearning.core.common.SecretRedactor
import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.model.BuildArtifact
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.BuildStatus
import com.example.jenkinslearning.domain.model.BuildSummary
import com.example.jenkinslearning.domain.model.ConsoleLog
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.JobSummary
import com.example.jenkinslearning.domain.model.QueuedBuild
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.domain.model.TestSummary
import com.example.jenkinslearning.domain.repository.JenkinsRepository
import kotlinx.coroutines.delay

/**
 * Built-in sample data so the app works and can be explored without a Jenkins
 * server. Every model it returns has source = SAMPLE and the UI shows a
 * "Sample data" banner. It never pretends to be a real server.
 */
class SampleJenkinsRepository(
    private val redactor: SecretRedactor = SecretRedactor(),
    private val latencyMillis: Long = 350,
    private val now: () -> Long = System::currentTimeMillis,
) : JenkinsRepository {

    private val minute = 60_000L
    private val hour = 60 * minute

    private fun build(number: Int, status: BuildStatus, agoMillis: Long, durationMillis: Long?) =
        BuildSummary(number, status, startedAtMillis = now() - agoMillis, durationMillis = durationMillis)

    private fun jobs(): List<JobSummary> = listOf(
        JobSummary(
            name = "JenkinsLearningApp",
            fullName = "JenkinsLearningApp",
            status = BuildStatus.SUCCESS,
            lastBuild = build(42, BuildStatus.SUCCESS, 25 * minute, 4 * minute + 12_000),
            lastSuccessfulBuild = build(42, BuildStatus.SUCCESS, 25 * minute, 4 * minute + 12_000),
            isFolder = false,
            buildable = true,
        ),
        JobSummary(
            name = "android",
            fullName = "android",
            status = BuildStatus.UNKNOWN,
            lastBuild = null,
            lastSuccessfulBuild = null,
            isFolder = true,
            buildable = false,
        ),
        JobSummary(
            name = "pull-requests",
            fullName = "android/pull-requests",
            status = BuildStatus.FAILURE,
            lastBuild = build(17, BuildStatus.FAILURE, 2 * hour, 2 * minute + 40_000),
            lastSuccessfulBuild = build(15, BuildStatus.SUCCESS, 26 * hour, 3 * minute + 5_000),
            isFolder = false,
            buildable = true,
        ),
        JobSummary(
            name = "nightly-lint",
            fullName = "nightly-lint",
            status = BuildStatus.UNSTABLE,
            lastBuild = build(8, BuildStatus.UNSTABLE, 9 * hour, 6 * minute),
            lastSuccessfulBuild = build(6, BuildStatus.SUCCESS, 57 * hour, 5 * minute + 30_000),
            isFolder = false,
            buildable = true,
        ),
        JobSummary(
            name = "docs-site",
            fullName = "docs-site",
            status = BuildStatus.IN_PROGRESS,
            lastBuild = build(5, BuildStatus.IN_PROGRESS, 1 * minute, 0),
            lastSuccessfulBuild = build(4, BuildStatus.SUCCESS, 30 * hour, 90_000),
            isFolder = false,
            buildable = true,
        ),
        JobSummary(
            name = "release-signing",
            fullName = "release-signing",
            status = BuildStatus.DISABLED,
            lastBuild = null,
            lastSuccessfulBuild = null,
            isFolder = false,
            buildable = false,
        ),
    )

    override suspend fun getServerOverview(): Outcome<ServerOverview> {
        delay(latencyMillis)
        return Outcome.Success(
            ServerOverview(
                baseUrl = "sample://jenkins",
                jenkinsVersion = "sample",
                jobs = jobs(),
                source = DataSource.SAMPLE,
            ),
        )
    }

    override suspend fun getJobDetails(jobFullName: String): Outcome<JobDetails> {
        delay(latencyMillis)
        val job = jobs().firstOrNull { it.fullName == jobFullName && !it.isFolder }
            ?: return Outcome.Failure(JenkinsError.NotFound)
        val last = job.lastBuild?.number ?: 0
        val history = (last downTo (last - 6).coerceAtLeast(1)).mapIndexed { index, number ->
            val status = when {
                number == last -> job.lastBuild?.status ?: BuildStatus.UNKNOWN
                number == job.lastSuccessfulBuild?.number -> BuildStatus.SUCCESS
                index % 3 == 2 -> BuildStatus.FAILURE
                else -> BuildStatus.SUCCESS
            }
            build(number, status, (index + 1) * 3 * hour, (3 * minute) + index * 7_000L)
        }
        return Outcome.Success(
            JobDetails(
                name = job.name,
                fullName = job.fullName,
                description = "Sample job. Switch to Live mode in Settings to see your Jenkins jobs.",
                status = job.status,
                buildable = job.buildable,
                inQueue = false,
                isParameterized = job.fullName == "nightly-lint",
                builds = history,
                lastSuccessfulBuildNumber = job.lastSuccessfulBuild?.number,
                lastFailedBuildNumber = history.firstOrNull { it.status == BuildStatus.FAILURE }?.number,
                source = DataSource.SAMPLE,
            ),
        )
    }

    override suspend fun getBuildDetails(jobFullName: String, buildNumber: Int): Outcome<BuildDetails> {
        delay(latencyMillis)
        val job = jobs().firstOrNull { it.fullName == jobFullName && !it.isFolder }
            ?: return Outcome.Failure(JenkinsError.NotFound)
        val isLatest = buildNumber == job.lastBuild?.number
        val status = if (isLatest) job.lastBuild?.status ?: BuildStatus.UNKNOWN else BuildStatus.SUCCESS
        val building = status == BuildStatus.IN_PROGRESS
        val root = "sample://jenkins/job/${job.fullName}/$buildNumber/"
        return Outcome.Success(
            BuildDetails(
                jobFullName = jobFullName,
                number = buildNumber,
                displayName = "${job.fullName} #$buildNumber",
                status = status,
                building = building,
                startedAtMillis = now() - 25 * minute,
                durationMillis = if (building) null else 4 * minute + 12_000,
                estimatedDurationMillis = 4 * minute,
                artifacts = if (building || status == BuildStatus.FAILURE) emptyList() else listOf(
                    BuildArtifact("app-debug.apk", "app/build/outputs/apk/debug/app-debug.apk", root + "artifact/app/build/outputs/apk/debug/app-debug.apk"),
                ),
                tests = when (status) {
                    BuildStatus.FAILURE -> TestSummary(total = 58, failed = 2, skipped = 0)
                    BuildStatus.IN_PROGRESS -> null
                    else -> TestSummary(total = 58, failed = 0, skipped = 1)
                },
                source = DataSource.SAMPLE,
            ),
        )
    }

    override suspend fun getConsoleLog(jobFullName: String, buildNumber: Int): Outcome<ConsoleLog> {
        delay(latencyMillis)
        if (jobs().none { it.fullName == jobFullName && !it.isFolder }) return Outcome.Failure(JenkinsError.NotFound)
        val redacted = redactor.redact(sampleConsole(jobFullName, buildNumber))
        return Outcome.Success(ConsoleLog(redacted.text, truncated = false, redactions = redacted.redactions, source = DataSource.SAMPLE))
    }

    override suspend fun triggerBuild(jobFullName: String): Outcome<QueuedBuild> {
        delay(latencyMillis)
        val job = jobs().firstOrNull { it.fullName == jobFullName } ?: return Outcome.Failure(JenkinsError.NotFound)
        if (!job.buildable) return Outcome.Failure(JenkinsError.JobNotBuildable)
        // Nothing is sent anywhere; the UI makes that explicit for SAMPLE results.
        return Outcome.Success(QueuedBuild(jobFullName, queueItemId = null, source = DataSource.SAMPLE))
    }

    private fun sampleConsole(job: String, number: Int): String = """
        |SAMPLE CONSOLE OUTPUT (not from a real Jenkins server)
        |Started by user developer
        |Obtained Jenkinsfile from git https://github.com/example/JenkinsLearningApp.git
        |[Pipeline] Start of Pipeline
        |[Pipeline] node
        |Running on Built-In Node in C:\ProgramData\Jenkins\.jenkins\workspace\$job
        |[Pipeline] stage (Checkout)
        |Checking out Revision 3f9c2a1b (refs/remotes/origin/main)
        |[Pipeline] stage (Verify environment)
        |[$job] Running batch script
        |JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
        |ANDROID_HOME=C:\Android\Sdk
        |openjdk version "21.0.8" 2025-07-15 LTS
        |[Pipeline] stage (Unit tests)
        |> gradlew.bat --no-daemon --stacktrace :app:testDebugUnitTest
        |BUILD SUCCESSFUL in 1m 48s
        |[Pipeline] stage (Credentials demo)
        |[Pipeline] withCredentials
        |Masking supported pattern matches of %SAMPLE_TOKEN%
        |> gradlew.bat --no-daemon :app:verifySampleToken
        |SAMPLE_TOKEN is available to the build. The value is intentionally not printed.
        |# A badly written debug line that Jenkins did NOT mask, because the value
        |# wasn't bound with withCredentials. The app's redactor masks it:
        |debug: DEPLOY_PASSWORD=hunter2-demo-value
        |[Pipeline] stage (Build debug APK)
        |> gradlew.bat --no-daemon :app:assembleDebug
        |BUILD SUCCESSFUL in 2m 02s
        |[Pipeline] archiveArtifacts
        |Archiving artifacts
        |[Pipeline] junit
        |Recording test results
        |Finished: SUCCESS (sample build #$number)
    """.trimMargin()
}
