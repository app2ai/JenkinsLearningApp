package com.example.jenkinslearning.testutil

import com.example.jenkinslearning.domain.model.AppBuildInfo
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.BuildStatus
import com.example.jenkinslearning.domain.model.BuildSummary
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.JobSummary
import com.example.jenkinslearning.domain.model.ServerOverview

/** Reads a file from src/test/resources. */
fun resource(path: String): String =
    requireNotNull(Thread.currentThread().contextClassLoader?.getResource(path)) { "Missing test resource $path" }.readText()

object Fixtures {

    fun build(number: Int, status: BuildStatus = BuildStatus.SUCCESS, startedAt: Long? = 1_000L * number, duration: Long? = 60_000L) =
        BuildSummary(number, status, startedAt, duration)

    fun job(
        fullName: String,
        status: BuildStatus = BuildStatus.SUCCESS,
        lastBuild: BuildSummary? = build(1),
        lastSuccessful: BuildSummary? = lastBuild,
        isFolder: Boolean = false,
        buildable: Boolean = !isFolder,
    ) = JobSummary(
        name = fullName.substringAfterLast('/'),
        fullName = fullName,
        status = status,
        lastBuild = lastBuild,
        lastSuccessfulBuild = lastSuccessful,
        isFolder = isFolder,
        buildable = buildable,
    )

    fun overview(jobs: List<JobSummary>, source: DataSource = DataSource.LIVE, version: String? = "2.528.1") =
        ServerOverview(baseUrl = "http://10.0.2.2:8080/", jenkinsVersion = version, jobs = jobs, source = source)

    fun jobDetails(fullName: String = "app", buildable: Boolean = true, source: DataSource = DataSource.LIVE) = JobDetails(
        name = fullName,
        fullName = fullName,
        description = null,
        status = BuildStatus.SUCCESS,
        buildable = buildable,
        inQueue = false,
        isParameterized = false,
        builds = listOf(build(2), build(1)),
        lastSuccessfulBuildNumber = 2,
        lastFailedBuildNumber = null,
        source = source,
    )

    fun buildDetails(
        number: Int = 7,
        building: Boolean = false,
        source: DataSource = DataSource.LIVE,
    ) = BuildDetails(
        jobFullName = "app",
        number = number,
        displayName = "app #$number",
        status = if (building) BuildStatus.IN_PROGRESS else BuildStatus.SUCCESS,
        building = building,
        startedAtMillis = 1_000L,
        durationMillis = if (building) null else 60_000L,
        estimatedDurationMillis = 60_000L,
        artifacts = emptyList(),
        tests = null,
        source = source,
    )

    val buildInfo = AppBuildInfo(
        defaultJenkinsBaseUrl = "http://10.0.2.2:8080/",
        ciBuildNumber = "local",
        ciGitCommit = "local",
        versionName = "1.0.0",
        isDebugBuild = true,
    )
}
