package com.example.jenkinslearning.data.mapper

import com.example.jenkinslearning.core.network.JenkinsJobPath
import com.example.jenkinslearning.data.dto.BuildDto
import com.example.jenkinslearning.data.dto.BuildRefDto
import com.example.jenkinslearning.data.dto.JobDto
import com.example.jenkinslearning.data.dto.JobSummaryDto
import com.example.jenkinslearning.data.dto.ServerDto
import com.example.jenkinslearning.domain.model.BuildArtifact
import com.example.jenkinslearning.domain.model.BuildDetails
import com.example.jenkinslearning.domain.model.BuildStatus
import com.example.jenkinslearning.domain.model.BuildSummary
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.JobDetails
import com.example.jenkinslearning.domain.model.JobSummary
import com.example.jenkinslearning.domain.model.ServerOverview
import com.example.jenkinslearning.domain.model.TestSummary

/** DTO -> domain mapping. Pure functions, unit-tested in JenkinsMappersTest. */
object JenkinsMappers {

    /** Maps the job list "color" (blue, red, yellow, ..., *_anime) to a status. */
    fun statusFromColor(color: String?): BuildStatus {
        if (color == null) return BuildStatus.UNKNOWN
        if (color.endsWith("_anime")) return BuildStatus.IN_PROGRESS
        return when (color) {
            "blue" -> BuildStatus.SUCCESS
            "yellow" -> BuildStatus.UNSTABLE
            "red" -> BuildStatus.FAILURE
            "aborted" -> BuildStatus.ABORTED
            "notbuilt" -> BuildStatus.NOT_BUILT
            "disabled", "grey" -> BuildStatus.DISABLED
            else -> BuildStatus.UNKNOWN
        }
    }

    /** Maps a build "result" (null while running). */
    fun statusFromResult(result: String?, building: Boolean?): BuildStatus {
        if (building == true) return BuildStatus.IN_PROGRESS
        return when (result) {
            "SUCCESS" -> BuildStatus.SUCCESS
            "UNSTABLE" -> BuildStatus.UNSTABLE
            "FAILURE" -> BuildStatus.FAILURE
            "ABORTED" -> BuildStatus.ABORTED
            "NOT_BUILT" -> BuildStatus.NOT_BUILT
            else -> BuildStatus.UNKNOWN
        }
    }

    fun toBuildSummary(dto: BuildRefDto?): BuildSummary? {
        val number = dto?.number ?: return null
        return BuildSummary(
            number = number,
            status = statusFromResult(dto.result, dto.building),
            startedAtMillis = dto.timestamp?.takeIf { it > 0 },
            durationMillis = dto.duration?.takeIf { it >= 0 },
        )
    }

    fun toServerOverview(dto: ServerDto?, baseUrl: String, jenkinsVersion: String?): ServerOverview =
        ServerOverview(
            baseUrl = baseUrl,
            jenkinsVersion = jenkinsVersion?.takeIf { it.isNotBlank() },
            jobs = dto?.jobs.orEmpty().flatMap { flattenJob(it, parentFullName = null) },
            source = DataSource.LIVE,
        )

    /** A folder becomes one entry with isFolder = true, followed by its children. */
    private fun flattenJob(dto: JobSummaryDto, parentFullName: String?): List<JobSummary> {
        val name = dto.name?.takeIf { it.isNotBlank() } ?: return emptyList()
        val fullName = dto.fullName?.takeIf { it.isNotBlank() }
            ?: if (parentFullName == null) name else "$parentFullName/$name"
        val isFolder = dto.jobs != null || isFolderClass(dto.className)
        val self = JobSummary(
            name = name,
            fullName = fullName,
            status = if (isFolder) BuildStatus.UNKNOWN else statusFromColor(dto.color),
            lastBuild = toBuildSummary(dto.lastBuild),
            lastSuccessfulBuild = toBuildSummary(dto.lastSuccessfulBuild),
            isFolder = isFolder,
            buildable = !isFolder && dto.buildable == true,
        )
        val children = dto.jobs.orEmpty().flatMap { flattenJob(it, parentFullName = fullName) }
        return listOf(self) + children
    }

    private fun isFolderClass(className: String?): Boolean =
        className != null && (className.endsWith("Folder") || className.endsWith("MultiBranchProject"))

    fun toJobDetails(dto: JobDto, requestedFullName: String): JobDetails {
        val parameterized = dto.properties.orEmpty().any { !it.parameterDefinitions.isNullOrEmpty() }
        return JobDetails(
            name = dto.name ?: requestedFullName.substringAfterLast('/'),
            fullName = dto.fullName ?: requestedFullName,
            description = dto.description?.takeIf { it.isNotBlank() },
            status = statusFromColor(dto.color),
            buildable = dto.buildable == true,
            inQueue = dto.inQueue == true,
            isParameterized = parameterized,
            builds = dto.builds.orEmpty().mapNotNull(::toBuildSummary).sortedByDescending { it.number },
            lastSuccessfulBuildNumber = dto.lastSuccessfulBuild?.number,
            lastFailedBuildNumber = dto.lastFailedBuild?.number,
            source = DataSource.LIVE,
        )
    }

    /**
     * Artifact URLs are built from the app's configured base URL, not Jenkins'
     * "url" field. Jenkins reports URLs using its own configured root URL
     * (often http://localhost:8080/), which an emulator or phone can't reach.
     */
    fun toBuildDetails(dto: BuildDto, jobFullName: String, requestedNumber: Int, baseUrl: String): BuildDetails {
        val number = dto.number ?: requestedNumber
        val buildRoot = baseUrl.trimEnd('/') + "/" + JenkinsJobPath.encode(jobFullName) + "/" + number + "/"
        val tests = dto.actions.orEmpty()
            .firstOrNull { it.totalCount != null }
            ?.let { TestSummary(total = it.totalCount ?: 0, failed = it.failCount ?: 0, skipped = it.skipCount ?: 0) }
        return BuildDetails(
            jobFullName = jobFullName,
            number = number,
            displayName = dto.fullDisplayName,
            status = statusFromResult(dto.result, dto.building),
            building = dto.building == true,
            startedAtMillis = dto.timestamp?.takeIf { it > 0 },
            durationMillis = dto.duration?.takeIf { it > 0 },
            estimatedDurationMillis = dto.estimatedDuration?.takeIf { it > 0 },
            artifacts = dto.artifacts.orEmpty().mapNotNull { artifact ->
                val path = artifact.relativePath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                BuildArtifact(
                    fileName = artifact.fileName ?: path.substringAfterLast('/'),
                    relativePath = path,
                    downloadUrl = buildRoot + "artifact/" +
                        path.split('/').joinToString("/") { JenkinsJobPath.encodeSegment(it) },
                )
            },
            tests = tests,
            source = DataSource.LIVE,
        )
    }

    /** Parses the queue item id from a Location header like ".../queue/item/42/". */
    fun queueItemIdFromLocation(location: String?): Long? =
        location?.let { Regex("""/queue/item/(\d+)/?$""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
}
