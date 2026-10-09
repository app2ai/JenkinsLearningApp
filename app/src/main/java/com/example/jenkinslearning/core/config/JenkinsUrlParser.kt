package com.example.jenkinslearning.core.config

import com.example.jenkinslearning.domain.error.UrlProblem
import java.net.URI
import java.net.URISyntaxException

sealed interface UrlParseResult {
    data class Valid(
        /** Normalized URL, always ending with "/" as Retrofit requires. */
        val baseUrl: String,
        val host: String,
        val isCleartext: Boolean,
    ) : UrlParseResult

    data class Invalid(val problem: UrlProblem) : UrlParseResult
}

/**
 * Validates a Jenkins base URL from user input or BuildConfig.
 *
 * Rules (the same rules app/build.gradle.kts applies to JENKINS_BASE_URL):
 *  - http or https only
 *  - a host is required
 *  - no user:password@ part, since credentials in URLs end up in logs and history
 *  - no query string or fragment
 *  - a context path such as /jenkins/ is allowed
 */
object JenkinsUrlParser {

    fun parse(raw: String?): UrlParseResult {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty()) return UrlParseResult.Invalid(UrlProblem.Blank)
        // "10.0.2.2:8080" or "jenkins.local" without a scheme is a common typo;
        // say "add http(s)://" instead of a generic "malformed".
        if (!input.contains("://")) return UrlParseResult.Invalid(UrlProblem.UnsupportedScheme)

        val uri = try {
            URI(input)
        } catch (_: URISyntaxException) {
            return UrlParseResult.Invalid(UrlProblem.Malformed)
        }

        val scheme = uri.scheme?.lowercase()
            ?: return UrlParseResult.Invalid(UrlProblem.UnsupportedScheme)
        if (scheme != "http" && scheme != "https") {
            return UrlParseResult.Invalid(UrlProblem.UnsupportedScheme)
        }
        if (uri.rawUserInfo != null) return UrlParseResult.Invalid(UrlProblem.ContainsCredentials)
        val host = uri.host
        if (host.isNullOrBlank()) return UrlParseResult.Invalid(UrlProblem.MissingHost)
        if (uri.rawQuery != null || uri.rawFragment != null) {
            return UrlParseResult.Invalid(UrlProblem.ContainsQueryOrFragment)
        }

        val port = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty().let { if (it.endsWith("/")) it else "$it/" }
        return UrlParseResult.Valid(
            baseUrl = "$scheme://${host.lowercase()}$port$path",
            host = host.lowercase(),
            isCleartext = scheme == "http",
        )
    }
}
