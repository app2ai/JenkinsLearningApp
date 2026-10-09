package com.example.jenkinslearning.core.config

import com.example.jenkinslearning.domain.error.UrlProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JenkinsUrlParserTest {

    private fun valid(input: String) = JenkinsUrlParser.parse(input) as UrlParseResult.Valid
    private fun problem(input: String?) = (JenkinsUrlParser.parse(input) as UrlParseResult.Invalid).problem

    @Test
    fun `emulator URL is valid, cleartext, and gets a trailing slash`() {
        val result = valid("http://10.0.2.2:8080")
        assertEquals("http://10.0.2.2:8080/", result.baseUrl)
        assertEquals("10.0.2.2", result.host)
        assertTrue(result.isCleartext)
    }

    @Test
    fun `https URL with context path is kept`() {
        val result = valid("  https://Jenkins.Example.com/jenkins  ")
        assertEquals("https://jenkins.example.com/jenkins/", result.baseUrl)
        assertFalse(result.isCleartext)
    }

    @Test
    fun `blank and null are rejected`() {
        assertEquals(UrlProblem.Blank, problem(null))
        assertEquals(UrlProblem.Blank, problem("   "))
    }

    @Test
    fun `non-http schemes are rejected`() {
        assertEquals(UrlProblem.UnsupportedScheme, problem("ftp://host/"))
        assertEquals(UrlProblem.UnsupportedScheme, problem("10.0.2.2:8080"))
    }

    @Test
    fun `credentials in the URL are rejected`() {
        assertEquals(UrlProblem.ContainsCredentials, problem("http://admin:11aa22bb@10.0.2.2:8080/"))
    }

    @Test
    fun `query and fragment are rejected`() {
        assertEquals(UrlProblem.ContainsQueryOrFragment, problem("http://host:8080/?token=abc"))
        assertEquals(UrlProblem.ContainsQueryOrFragment, problem("http://host:8080/#x"))
    }

    @Test
    fun `missing host and malformed input are rejected`() {
        assertEquals(UrlProblem.MissingHost, problem("http:///path"))
        assertEquals(UrlProblem.Malformed, problem("http://host name with spaces/"))
    }
}
