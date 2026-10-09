package com.example.jenkinslearning.presentation.components

import com.example.jenkinslearning.domain.error.JenkinsError
import com.example.jenkinslearning.domain.error.UrlProblem
import com.example.jenkinslearning.domain.model.BuildStatus

/**
 * Turns errors into safe, actionable messages. Messages are built only from
 * the typed error, so no URL, token, header or server output can leak into
 * the UI through this path.
 */
object ErrorMessages {

    fun title(error: JenkinsError): String = when (error) {
        JenkinsError.NotConfigured -> "Jenkins isn't configured"
        is JenkinsError.InvalidServerUrl -> "Invalid server URL"
        JenkinsError.Unauthorized -> "Authentication failed (401)"
        JenkinsError.Forbidden -> "Permission denied (403)"
        JenkinsError.NotFound -> "Not found (404)"
        is JenkinsError.RateLimited -> "Too many requests (429)"
        is JenkinsError.ServerError -> "Jenkins server error (${error.httpCode})"
        is JenkinsError.UnexpectedStatus -> "Unexpected response (${error.httpCode})"
        JenkinsError.BuildRequiresParameters -> "This job needs parameters"
        JenkinsError.JobNotBuildable -> "This job can't be built"
        JenkinsError.Timeout -> "Request timed out"
        JenkinsError.HostUnreachable -> "Can't reach Jenkins"
        JenkinsError.CleartextBlocked -> "HTTP blocked by Android"
        JenkinsError.TlsFailure -> "Secure connection failed"
        JenkinsError.InvalidResponse -> "Unexpected response from server"
        is JenkinsError.Unexpected -> "Something went wrong"
    }

    fun detail(error: JenkinsError): String = when (error) {
        JenkinsError.NotConfigured -> "Open Settings and enter your Jenkins URL, or use Sample mode."
        is JenkinsError.InvalidServerUrl -> urlProblem(error.reason)
        JenkinsError.Unauthorized ->
            "Check the username and API token in Settings. Create a token in Jenkins under your user → Security → API Token."
        JenkinsError.Forbidden ->
            "Your Jenkins user is missing a permission (Overall/Read, Job/Read or Job/Build), or anonymous access is disabled and no token is set."
        JenkinsError.NotFound -> "The job or build doesn't exist, or your user can't see it."
        is JenkinsError.RateLimited ->
            error.retryAfterSeconds?.let { "Try again in $it seconds." } ?: "Try again later."
        is JenkinsError.ServerError -> "Jenkins (or a proxy in front of it) failed. Check the Jenkins system log."
        is JenkinsError.UnexpectedStatus -> "Jenkins answered with an unexpected status."
        JenkinsError.BuildRequiresParameters -> "Trigger it from Jenkins with parameters, or give every parameter a default."
        JenkinsError.JobNotBuildable -> "It's disabled, a folder, or otherwise not buildable."
        JenkinsError.Timeout -> "Jenkins didn't answer in time. Is it running and reachable from this device?"
        JenkinsError.HostUnreachable ->
            "On the emulator use http://10.0.2.2:8080/ (not localhost). On a phone, use adb reverse or your PC's LAN IP, and allow port 8080 in Windows Firewall."
        JenkinsError.CleartextBlocked ->
            "This build doesn't allow http:// to that host. Debug builds allow it only for 10.0.2.2, 127.0.0.1 and localhost. Use HTTPS otherwise."
        JenkinsError.TlsFailure -> "The certificate isn't trusted by this device (self-signed?). Use a trusted certificate."
        JenkinsError.InvalidResponse -> "The URL may not point at Jenkins, or a proxy/login page answered instead."
        is JenkinsError.Unexpected -> "Please try again. (${error.type})"
    }

    fun urlProblem(problem: UrlProblem): String = when (problem) {
        UrlProblem.Blank -> "Enter the Jenkins URL, e.g. http://10.0.2.2:8080/"
        UrlProblem.Malformed -> "That isn't a valid URL."
        UrlProblem.UnsupportedScheme -> "Use a URL starting with http:// or https://"
        UrlProblem.MissingHost -> "The URL needs a host name or IP address."
        UrlProblem.ContainsCredentials -> "Don't put a username or token in the URL. Use the separate fields."
        UrlProblem.ContainsQueryOrFragment -> "Remove the ?query or #fragment part."
    }

    fun status(status: BuildStatus): String = when (status) {
        BuildStatus.SUCCESS -> "Success"
        BuildStatus.UNSTABLE -> "Unstable"
        BuildStatus.FAILURE -> "Failed"
        BuildStatus.ABORTED -> "Aborted"
        BuildStatus.NOT_BUILT -> "Not built"
        BuildStatus.DISABLED -> "Disabled"
        BuildStatus.IN_PROGRESS -> "Building"
        BuildStatus.UNKNOWN -> "Unknown"
    }
}
