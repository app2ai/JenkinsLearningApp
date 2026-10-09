package com.example.jenkinslearning.domain.error

/**
 * Every failure the app can show to the user.
 *
 * Errors carry no raw exception messages, URLs with credentials, headers or
 * response bodies, only what's needed to explain the problem. Exception
 * messages from networking libraries can include request URLs or server
 * output, so they're never forwarded to the UI or logs.
 */
sealed interface JenkinsError {

    /** No server URL or credentials are configured for live mode. */
    data object NotConfigured : JenkinsError

    /** The URL the user typed (or BuildConfig default) is not usable. */
    data class InvalidServerUrl(val reason: UrlProblem) : JenkinsError

    /** HTTP 401: missing or wrong username / API token. */
    data object Unauthorized : JenkinsError

    /** HTTP 403: authenticated but lacking a permission, or CSRF crumb rejected. */
    data object Forbidden : JenkinsError

    /** HTTP 404: job, build or endpoint doesn't exist (or is hidden from this user). */
    data object NotFound : JenkinsError

    /** HTTP 429: too many requests. [retryAfterSeconds] comes from the Retry-After header when present. */
    data class RateLimited(val retryAfterSeconds: Long?) : JenkinsError

    /** HTTP 5xx: Jenkins or a proxy in front of it failed. */
    data class ServerError(val httpCode: Int) : JenkinsError

    /** Any other non-2xx status. */
    data class UnexpectedStatus(val httpCode: Int) : JenkinsError

    /** HTTP 400 on /build: the job has parameters and needs /buildWithParameters. */
    data object BuildRequiresParameters : JenkinsError

    /** The job is disabled or otherwise can't be built. */
    data object JobNotBuildable : JenkinsError

    data object Timeout : JenkinsError

    /** DNS failure or connection refused. Usually a wrong host, port or emulator address. */
    data object HostUnreachable : JenkinsError

    /** Android's network security config blocked an http:// request. */
    data object CleartextBlocked : JenkinsError

    /** TLS handshake / certificate problem (e.g. self-signed certificate). */
    data object TlsFailure : JenkinsError

    /** The server answered, but not with the JSON we expected (wrong URL, proxy page...). */
    data object InvalidResponse : JenkinsError

    /** Anything else. Only the exception's class name is kept, never its message. */
    data class Unexpected(val type: String) : JenkinsError
}

enum class UrlProblem {
    Blank,
    Malformed,
    UnsupportedScheme,
    MissingHost,
    ContainsCredentials,
    ContainsQueryOrFragment,
}
