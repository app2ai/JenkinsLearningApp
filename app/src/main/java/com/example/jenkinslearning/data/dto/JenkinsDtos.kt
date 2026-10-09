package com.example.jenkinslearning.data.dto

import com.google.gson.annotations.SerializedName

// Jenkins REST API response shapes (subset). Every field is nullable: Gson
// creates objects without calling constructors, so a missing JSON field is
// null at runtime even if the Kotlin type says otherwise. Mapping to domain
// models (data/mapper) decides how to handle absent values.

/** GET /api/json */
data class ServerDto(
    @SerializedName("jobs") val jobs: List<JobSummaryDto>?,
    @SerializedName("description") val description: String?,
)

data class JobSummaryDto(
    @SerializedName("_class") val className: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("url") val url: String?,
    /** blue, red, yellow, aborted, notbuilt, disabled; "_anime" suffix while building. */
    @SerializedName("color") val color: String?,
    @SerializedName("buildable") val buildable: Boolean?,
    @SerializedName("lastBuild") val lastBuild: BuildRefDto?,
    @SerializedName("lastSuccessfulBuild") val lastSuccessfulBuild: BuildRefDto?,
    /** Present for folders (one level is requested via the tree parameter). */
    @SerializedName("jobs") val jobs: List<JobSummaryDto>?,
)

data class BuildRefDto(
    @SerializedName("number") val number: Int?,
    /** SUCCESS, UNSTABLE, FAILURE, ABORTED, NOT_BUILT, or null while building. */
    @SerializedName("result") val result: String?,
    @SerializedName("timestamp") val timestamp: Long?,
    @SerializedName("duration") val duration: Long?,
    @SerializedName("building") val building: Boolean?,
)

/** GET /job/{name}/api/json */
data class JobDto(
    @SerializedName("_class") val className: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("color") val color: String?,
    @SerializedName("buildable") val buildable: Boolean?,
    @SerializedName("inQueue") val inQueue: Boolean?,
    @SerializedName("builds") val builds: List<BuildRefDto>?,
    @SerializedName("lastSuccessfulBuild") val lastSuccessfulBuild: BuildRefDto?,
    @SerializedName("lastFailedBuild") val lastFailedBuild: BuildRefDto?,
    @SerializedName("property") val properties: List<JobPropertyDto>?,
)

data class JobPropertyDto(
    @SerializedName("parameterDefinitions") val parameterDefinitions: List<ParameterDefinitionDto>?,
)

data class ParameterDefinitionDto(
    @SerializedName("name") val name: String?,
)

/** GET /job/{name}/{number}/api/json */
data class BuildDto(
    @SerializedName("number") val number: Int?,
    @SerializedName("fullDisplayName") val fullDisplayName: String?,
    @SerializedName("result") val result: String?,
    @SerializedName("building") val building: Boolean?,
    @SerializedName("timestamp") val timestamp: Long?,
    @SerializedName("duration") val duration: Long?,
    @SerializedName("estimatedDuration") val estimatedDuration: Long?,
    @SerializedName("url") val url: String?,
    @SerializedName("artifacts") val artifacts: List<ArtifactDto>?,
    /** Most actions are empty objects; the JUnit one has the counts below. */
    @SerializedName("actions") val actions: List<BuildActionDto>?,
)

data class ArtifactDto(
    @SerializedName("fileName") val fileName: String?,
    @SerializedName("relativePath") val relativePath: String?,
)

data class BuildActionDto(
    @SerializedName("_class") val className: String?,
    @SerializedName("totalCount") val totalCount: Int?,
    @SerializedName("failCount") val failCount: Int?,
    @SerializedName("skipCount") val skipCount: Int?,
)

/** GET /crumbIssuer/api/json */
data class CrumbDto(
    @SerializedName("crumb") val crumb: String?,
    @SerializedName("crumbRequestField") val crumbRequestField: String?,
)
