package com.example.jenkinslearning.core.common

import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.error.UrlProblem
import com.example.jenkinslearning.domain.model.SessionCredentials
import com.example.jenkinslearning.feature.settings.SettingsIntent
import com.example.jenkinslearning.presentation.components.ErrorMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Guards against secrets leaking through toString() (logs, crash reports) or error text. */
class CredentialSafetyTest {

    private val token = "11a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7"

    @Test
    fun `session credentials never print the token`() {
        val text = SessionCredentials("dev", token).toString()
        assertFalse(text.contains(token))
        assertEquals("SessionCredentials(username=dev, apiToken=****)", text)
    }

    @Test
    fun `the Save intent never prints the token`() {
        assertFalse(SettingsIntent.Save(token).toString().contains(token))
    }

    @Test
    fun `credentials still compare by value`() {
        assertEquals(SessionCredentials("dev", token), SessionCredentials("dev", token))
    }

    @Test
    fun `every error message is built from the error type only`() {
        val errors = listOf(
            JenkinsError.NotConfigured,
            JenkinsError.InvalidServerUrl(UrlProblem.ContainsCredentials),
            JenkinsError.Unauthorized,
            JenkinsError.Forbidden,
            JenkinsError.NotFound,
            JenkinsError.RateLimited(12),
            JenkinsError.ServerError(502),
            JenkinsError.UnexpectedStatus(418),
            JenkinsError.BuildRequiresParameters,
            JenkinsError.JobNotBuildable,
            JenkinsError.Timeout,
            JenkinsError.HostUnreachable,
            JenkinsError.CleartextBlocked,
            JenkinsError.TlsFailure,
            JenkinsError.InvalidResponse,
            JenkinsError.Unexpected("IllegalStateException"),
        )
        errors.forEach { error ->
            val text = ErrorMessages.title(error) + ErrorMessages.detail(error)
            assertFalse("Message for $error is blank", text.isBlank())
            assertFalse(text.contains(token))
        }
    }
}
