package com.example.jenkinslearning.core.network

import com.example.jenkinslearning.domain.model.SessionCredentials
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object HttpClientFactory {

    /**
     * Base client shared by all Jenkins servers. Per-server pieces (auth,
     * cookies) are added in [com.example.jenkinslearning.data.remote.JenkinsApiProvider].
     *
     * Logging is only enabled for debug builds, at BASIC level (method, URL,
     * status, timing). BASIC never logs headers or bodies; the redactHeader
     * calls are a safety net in case someone raises the level while debugging.
     */
    fun create(enableLogging: Boolean): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .apply {
                if (enableLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                            redactHeader("Cookie")
                            redactHeader("Set-Cookie")
                            redactHeader("Jenkins-Crumb")
                        },
                    )
                }
            }
            .build()
}

/**
 * Adds HTTP Basic auth (username + Jenkins API token) to requests going to the
 * configured Jenkins origin only. If a response redirects somewhere else, the
 * token isn't attached there. OkHttp also strips Authorization on cross-host
 * redirects, so this is defense in depth.
 *
 * Jenkins treats API-token Basic auth as exempt from CSRF crumbs, which is why
 * token auth is the recommended way for API clients.
 */
class JenkinsAuthInterceptor(
    private val credentials: () -> SessionCredentials?,
    private val serverUrl: HttpUrl,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val current = credentials()
        val sameOrigin = request.url.scheme == serverUrl.scheme &&
            request.url.host == serverUrl.host &&
            request.url.port == serverUrl.port
        if (current == null || !sameOrigin || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        val authorized = request.newBuilder()
            .header("Authorization", Credentials.basic(current.username, current.apiToken))
            .build()
        return chain.proceed(authorized)
    }
}

/**
 * Jenkins ties CSRF crumbs to the web session (JSESSIONID cookie), so the
 * crumb request and the POST that uses it must share cookies. Cookies are
 * kept in memory only and dropped with the client.
 */
class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            this.cookies += incoming
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }
}
