package com.example.jenkinslearning.data.mapper

import com.example.jenkinslearning.data.dto.BuildDto
import com.example.jenkinslearning.data.dto.JobDto
import com.example.jenkinslearning.data.dto.ServerDto
import com.example.jenkinslearning.domain.model.BuildStatus
import com.example.jenkinslearning.domain.model.DataSource
import com.example.jenkinslearning.domain.model.TestSummary
import com.example.jenkinslearning.testutil.resource
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses realistic Jenkins JSON (src/test/resources/jenkins) with the same Gson setup as the app. */
class JenkinsMappersTest {

    private val gson = Gson()

    @Test
    fun `color values map to statuses, anime means building`() {
        assertEquals(BuildStatus.SUCCESS, JenkinsMappers.statusFromColor("blue"))
        assertEquals(BuildStatus.UNSTABLE, JenkinsMappers.statusFromColor("yellow"))
        assertEquals(BuildStatus.FAILURE, JenkinsMappers.statusFromColor("red"))
        assertEquals(BuildStatus.IN_PROGRESS, JenkinsMappers.statusFromColor("red_anime"))
        assertEquals(BuildStatus.DISABLED, JenkinsMappers.statusFromColor("disabled"))
        assertEquals(BuildStatus.NOT_BUILT, JenkinsMappers.statusFromColor("notbuilt"))
        assertEquals(BuildStatus.UNKNOWN, JenkinsMappers.statusFromColor(null))
        assertEquals(BuildStatus.UNKNOWN, JenkinsMappers.statusFromColor("purple"))
    }

    @Test
    fun `build results map to statuses, building wins over result`() {
        assertEquals(BuildStatus.IN_PROGRESS, JenkinsMappers.statusFromResult(null, building = true))
        assertEquals(BuildStatus.ABORTED, JenkinsMappers.statusFromResult("ABORTED", building = false))
        assertEquals(BuildStatus.UNKNOWN, JenkinsMappers.statusFromResult(null, building = false))
    }

    @Test
    fun `server overview flattens folders and keeps the version header`() {
        val dto = gson.fromJson(resource("jenkins/server.json"), ServerDto::class.java)
        val overview = JenkinsMappers.toServerOverview(dto, "http://10.0.2.2:8080/", jenkinsVersion = "2.528.1")

        assertEquals("2.528.1", overview.jenkinsVersion)
        assertEquals(DataSource.LIVE, overview.source)
        assertEquals(listOf("JenkinsLearningApp", "android", "android/my app", "legacy"), overview.jobs.map { it.fullName })

        val folder = overview.jobs.single { it.fullName == "android" }
        assertTrue(folder.isFolder)
        assertFalse(folder.buildable)

        val nested = overview.jobs.single { it.fullName == "android/my app" }
        assertEquals(BuildStatus.IN_PROGRESS, nested.status)
        assertEquals(18, nested.lastBuild?.number)
        assertEquals(15, nested.lastSuccessfulBuild?.number)

        val legacy = overview.jobs.single { it.fullName == "legacy" }
        assertEquals(BuildStatus.DISABLED, legacy.status)
        assertNull(legacy.lastBuild)
        assertFalse(legacy.buildable)
    }

    @Test
    fun `missing fields don't crash mapping`() {
        val dto = gson.fromJson("""{"jobs":[{"name":"x"},{"fullName":"no-name"}]}""", ServerDto::class.java)
        val overview = JenkinsMappers.toServerOverview(dto, "http://h/", jenkinsVersion = " ")
        assertNull(overview.jenkinsVersion)
        assertEquals(listOf("x"), overview.jobs.map { it.fullName })
        assertEquals(BuildStatus.UNKNOWN, overview.jobs.single().status)
    }

    @Test
    fun `job details detect parameters and sort builds newest first`() {
        val plain = JenkinsMappers.toJobDetails(gson.fromJson(resource("jenkins/job.json"), JobDto::class.java), "JenkinsLearningApp")
        assertFalse(plain.isParameterized)
        assertEquals(listOf(42, 41), plain.builds.map { it.number })
        assertEquals(42, plain.lastSuccessfulBuildNumber)
        assertEquals(41, plain.lastFailedBuildNumber)
        assertNull(plain.description) // blank description becomes null

        val parameterized = JenkinsMappers.toJobDetails(
            gson.fromJson(resource("jenkins/job_parameterized.json"), JobDto::class.java),
            "JenkinsLearningApp",
        )
        assertTrue(parameterized.isParameterized)
    }

    @Test
    fun `build details read JUnit counts and build artifact URLs from the app's base URL`() {
        val dto = gson.fromJson(resource("jenkins/build.json"), BuildDto::class.java)
        val details = JenkinsMappers.toBuildDetails(dto, "android/my app", requestedNumber = 42, baseUrl = "http://10.0.2.2:8080/")

        assertEquals(BuildStatus.UNSTABLE, details.status)
        assertEquals(TestSummary(total = 58, failed = 2, skipped = 1), details.tests)
        assertEquals(55, details.tests?.passed)
        assertEquals(
            "http://10.0.2.2:8080/job/android/job/my%20app/42/artifact/app/build/outputs/apk/debug/app-debug.apk",
            details.artifacts[0].downloadUrl,
        )
        // Jenkins reported localhost:8080; the emulator couldn't reach that, so it's not used.
        assertFalse(details.artifacts.any { it.downloadUrl.contains("localhost") })
        assertTrue(details.artifacts[1].downloadUrl.endsWith("/artifact/app/build/reports/lint%20results.html"))
    }

    @Test
    fun `queue item id is parsed from the Location header`() {
        assertEquals(42L, JenkinsMappers.queueItemIdFromLocation("http://10.0.2.2:8080/queue/item/42/"))
        assertEquals(7L, JenkinsMappers.queueItemIdFromLocation("/queue/item/7"))
        assertNull(JenkinsMappers.queueItemIdFromLocation("http://10.0.2.2:8080/job/x/"))
        assertNull(JenkinsMappers.queueItemIdFromLocation(null))
    }
}
