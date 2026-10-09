package com.example.jenkinslearning.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

    private val redactor = SecretRedactor()

    @Test
    fun `key-value secrets keep the key and mask the value`() {
        val result = redactor.redact("debug: DEPLOY_PASSWORD=hunter2-demo-value\napi_key: \"abc123xyz\"")
        assertEquals("debug: DEPLOY_PASSWORD=****\napi_key: \"****\"", result.text)
        assertEquals(2, result.redactions)
    }

    @Test
    fun `authorization headers are masked`() {
        val result = redactor.redact("Authorization: Basic ZGV2OjExYWFiYmNjZGQ=")
        assertEquals("Authorization: Basic ****", result.text)
    }

    @Test
    fun `credentials in URLs are masked`() {
        val result = redactor.redact("Cloning https://dev:ghp_secretvalue@github.com/x/y.git")
        assertFalse(result.text.contains("ghp_secretvalue"))
        assertTrue(result.text.contains("https://****:****@github.com/x/y.git"))
    }

    @Test
    fun `well-known token formats are masked`() {
        val github = "ghp_" + "a".repeat(36)
        val aws = "AKIA" + "ABCDEFGHIJKLMNOP"
        val result = redactor.redact("token $github and key $aws")
        assertEquals("token **** and key ****", result.text)
    }

    @Test
    fun `private keys are masked`() {
        val pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEow\nIBAAK\n-----END RSA PRIVATE KEY-----"
        val result = redactor.redact("key:\n$pem\ndone")
        assertFalse(result.text.contains("MIIEow"))
        assertTrue(result.text.endsWith("done"))
    }

    @Test
    fun `known session secrets are masked wherever they appear`() {
        val token = "11f0c0ffee1234567890"
        val result = SecretRedactor { listOf(token) }.redact("echo $token ; curl -u dev:$token")
        assertFalse(result.text.contains(token))
        assertEquals(2, result.redactions)
    }

    @Test
    fun `short known secrets are ignored to avoid masking ordinary words`() {
        val result = SecretRedactor { listOf("dev") }.redact("developer dev")
        assertEquals("developer dev", result.text)
    }

    @Test
    fun `values Jenkins already masked are left alone`() {
        val result = redactor.redact("SAMPLE_TOKEN=****")
        assertEquals("SAMPLE_TOKEN=****", result.text)
        assertEquals(0, result.redactions)
    }

    @Test
    fun `ordinary build output is unchanged`() {
        val log = "> Task :app:testDebugUnitTest\nBUILD SUCCESSFUL in 1m 48s\nANDROID_HOME=C:\\Android\\Sdk"
        val result = redactor.redact(log)
        assertEquals(log, result.text)
        assertEquals(0, result.redactions)
    }
}
