package com.example.jenkinslearning.core.network

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Converts a Jenkins job full name into its URL path.
 *
 * Jenkins nests jobs inside folders with repeated "job/" segments:
 *   "JenkinsLearningApp"          -> "job/JenkinsLearningApp"
 *   "android/Jenkins Learning"    -> "job/android/job/Jenkins%20Learning"
 *
 * Each name segment is percent-encoded on its own, so spaces, "#", "?", "%"
 * and "+" in job names can't break or alter the request path. The result is
 * passed to Retrofit with @Path(encoded = true), so Retrofit doesn't encode it again.
 */
object JenkinsJobPath {

    fun encode(jobFullName: String): String {
        val segments = jobFullName.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        require(segments.isNotEmpty()) { "Job name is empty" }
        require(segments.none { it == "." || it == ".." }) { "Invalid job name segment" }
        return segments.joinToString(separator = "/") { "job/" + encodeSegment(it) }
    }

    /** RFC 3986 path-segment encoding (URLEncoder is form encoding, so fix "+" and "*"). */
    internal fun encodeSegment(segment: String): String =
        URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
}
