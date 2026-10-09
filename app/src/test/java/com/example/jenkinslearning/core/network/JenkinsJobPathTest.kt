package com.example.jenkinslearning.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class JenkinsJobPathTest {

    @Test
    fun `top-level job becomes a single job segment`() {
        assertEquals("job/JenkinsLearningApp", JenkinsJobPath.encode("JenkinsLearningApp"))
    }

    @Test
    fun `folders become repeated job segments`() {
        assertEquals("job/android/job/pull-requests", JenkinsJobPath.encode("android/pull-requests"))
    }

    @Test
    fun `spaces are percent-encoded, not turned into plus`() {
        assertEquals("job/android/job/my%20app", JenkinsJobPath.encode("android/my app"))
    }

    @Test
    fun `reserved characters cannot change the request path or query`() {
        // '#', '?', '%' and '+' would otherwise start a fragment/query or be mis-decoded.
        assertEquals("job/a%23b%3Fc%25d%2Be", JenkinsJobPath.encode("a#b?c%d+e"))
    }

    @Test
    fun `non-ascii names are UTF-8 encoded`() {
        assertEquals("job/caf%C3%A9", JenkinsJobPath.encode("café"))
    }

    @Test
    fun `empty segments and surrounding slashes are ignored`() {
        assertEquals("job/a/job/b", JenkinsJobPath.encode("/a//b/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank name is rejected`() {
        JenkinsJobPath.encode("  /  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dot-dot segments are rejected`() {
        JenkinsJobPath.encode("folder/../secret")
    }
}
