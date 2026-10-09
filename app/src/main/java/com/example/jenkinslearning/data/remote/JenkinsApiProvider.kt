package com.example.jenkinslearning.data.remote

import com.example.jenkinslearning.core.network.InMemoryCookieJar
import com.example.jenkinslearning.core.network.JenkinsAuthInterceptor
import com.example.jenkinslearning.domain.repository.SessionRepository
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Creates (and caches) a [JenkinsApi] for the currently configured server.
 *
 * The base URL is a runtime setting, but Retrofit fixes its base URL when it's
 * built, so a new Retrofit instance is created whenever the URL changes. The
 * per-server OkHttpClient comes from [baseClient].newBuilder(), so it shares
 * the connection pool and dispatcher instead of creating new ones.
 */
class JenkinsApiProvider(
    private val baseClient: OkHttpClient,
    private val gson: Gson,
    private val sessionRepository: SessionRepository,
) {
    private var cached: Pair<String, JenkinsApi>? = null

    @Synchronized
    fun forBaseUrl(baseUrl: String): JenkinsApi {
        cached?.let { (url, api) -> if (url == baseUrl) return api }
        return create(baseUrl).also { cached = baseUrl to it }
    }

    private fun create(baseUrl: String): JenkinsApi {
        val serverUrl = baseUrl.toHttpUrl()
        val client = baseClient.newBuilder()
            .addInterceptor(JenkinsAuthInterceptor(sessionRepository::current, serverUrl))
            .cookieJar(InMemoryCookieJar())
            .build()
        return Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(JenkinsApi::class.java)
    }
}
