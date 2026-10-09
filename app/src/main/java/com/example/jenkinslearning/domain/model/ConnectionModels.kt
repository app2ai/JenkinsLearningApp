package com.example.jenkinslearning.domain.model

enum class DataMode {
    /** Built-in sample data. No network calls. The default, so the app works with no Jenkins. */
    SAMPLE,

    /** Real Jenkins REST API calls. */
    LIVE,
}

/**
 * Non-secret connection settings. These are safe to persist (DataStore).
 * The API token is deliberately NOT part of this class: see [SessionCredentials].
 */
data class ConnectionSettings(
    val baseUrl: String,
    val username: String,
    val mode: DataMode,
)

/**
 * Jenkins username + API token for the current app process only.
 *
 * toString() is overridden on purpose: a data class would print the token
 * into logs, crash reports or debugger output.
 */
class SessionCredentials(val username: String, val apiToken: String) {
    override fun toString(): String = "SessionCredentials(username=$username, apiToken=****)"
    override fun equals(other: Any?): Boolean =
        other is SessionCredentials && other.username == username && other.apiToken == apiToken
    override fun hashCode(): Int = 31 * username.hashCode() + apiToken.hashCode()
}

/** Values baked in at build time from BuildConfig. None of them are secrets. */
data class AppBuildInfo(
    val defaultJenkinsBaseUrl: String,
    val ciBuildNumber: String,
    val ciGitCommit: String,
    val versionName: String,
    val isDebugBuild: Boolean,
) {
    val builtByCi: Boolean get() = ciBuildNumber != "local"
}
