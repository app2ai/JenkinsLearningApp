package com.example.jenkinslearning.data.remote

import com.example.jenkinslearning.data.dto.BuildDto
import com.example.jenkinslearning.data.dto.CrumbDto
import com.example.jenkinslearning.data.dto.JobDto
import com.example.jenkinslearning.data.dto.ServerDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Jenkins REST endpoints used by the app.
 *
 * - Paths are relative (no leading "/") so a Jenkins context path in the base
 *   URL, like https://host/jenkins/, is kept.
 * - `jobPath` is pre-encoded by JenkinsJobPath ("job/a/job/b"), so encoded = true.
 * - Every call returns Response<T> so the repository can read status codes and
 *   headers (X-Jenkins, Location, Retry-After) and map errors itself.
 * - The `tree` parameter asks Jenkins for only the fields the app needs.
 */
interface JenkinsApi {

    @GET("api/json")
    suspend fun getServer(@Query("tree") tree: String = SERVER_TREE): Response<ServerDto>

    @GET("{jobPath}/api/json")
    suspend fun getJob(
        @Path(value = "jobPath", encoded = true) jobPath: String,
        @Query("tree") tree: String = JOB_TREE,
    ): Response<JobDto>

    @GET("{jobPath}/{buildNumber}/api/json")
    suspend fun getBuild(
        @Path(value = "jobPath", encoded = true) jobPath: String,
        @Path("buildNumber") buildNumber: Int,
        @Query("tree") tree: String = BUILD_TREE,
    ): Response<BuildDto>

    /** Plain-text console. Streamed so a huge log isn't loaded into memory at once. */
    @Streaming
    @GET("{jobPath}/{buildNumber}/consoleText")
    suspend fun getConsoleText(
        @Path(value = "jobPath", encoded = true) jobPath: String,
        @Path("buildNumber") buildNumber: Int,
    ): Response<ResponseBody>

    /** Returns 201 Created with a Location header pointing at the queue item. */
    @POST("{jobPath}/build")
    suspend fun triggerBuild(
        @Path(value = "jobPath", encoded = true) jobPath: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<Unit>

    /** For parameterized jobs; Jenkins uses each parameter's default value. */
    @POST("{jobPath}/buildWithParameters")
    suspend fun triggerBuildWithParameters(
        @Path(value = "jobPath", encoded = true) jobPath: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<Unit>

    /** CSRF crumb. 404 means the crumb issuer is disabled. */
    @GET("crumbIssuer/api/json")
    suspend fun getCrumb(): Response<CrumbDto>

    companion object {
        private const val BUILD_REF = "number,result,timestamp,duration,building"
        private const val JOB_FIELDS =
            "_class,name,fullName,url,color,buildable,lastBuild[$BUILD_REF],lastSuccessfulBuild[$BUILD_REF]"

        /** Top-level jobs plus one level of jobs inside folders. */
        const val SERVER_TREE = "description,jobs[$JOB_FIELDS,jobs[$JOB_FIELDS]]"

        const val JOB_TREE =
            "_class,name,fullName,description,color,buildable,inQueue," +
                "builds[$BUILD_REF]{0,15}," +
                "lastSuccessfulBuild[number],lastFailedBuild[number]," +
                "property[parameterDefinitions[name]]"

        const val BUILD_TREE =
            "number,fullDisplayName,result,building,timestamp,duration,estimatedDuration,url," +
                "artifacts[fileName,relativePath]," +
                "actions[_class,totalCount,failCount,skipCount]"
    }
}
