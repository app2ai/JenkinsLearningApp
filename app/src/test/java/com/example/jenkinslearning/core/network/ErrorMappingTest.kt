package com.example.jenkinslearning.core.network

import com.example.jenkinslearning.domain.error.JenkinsError
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.MalformedJsonException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException

class ErrorMappingTest {

    @Test
    fun `HTTP statuses map to typed errors`() {
        assertEquals(JenkinsError.Unauthorized, HttpErrorMapper.fromStatus(401))
        assertEquals(JenkinsError.Forbidden, HttpErrorMapper.fromStatus(403))
        assertEquals(JenkinsError.NotFound, HttpErrorMapper.fromStatus(404))
        assertEquals(JenkinsError.RateLimited(30), HttpErrorMapper.fromStatus(429, "30"))
        assertEquals(JenkinsError.ServerError(500), HttpErrorMapper.fromStatus(500))
        assertEquals(JenkinsError.ServerError(503), HttpErrorMapper.fromStatus(503))
        assertEquals(JenkinsError.UnexpectedStatus(409), HttpErrorMapper.fromStatus(409))
    }

    @Test
    fun `Retry-After as an HTTP date or garbage is treated as unknown`() {
        assertEquals(JenkinsError.RateLimited(null), HttpErrorMapper.fromStatus(429, "Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(JenkinsError.RateLimited(null), HttpErrorMapper.fromStatus(429, "-5"))
        assertEquals(JenkinsError.RateLimited(null), HttpErrorMapper.fromStatus(429, null))
    }

    @Test
    fun `network exceptions map to actionable errors`() {
        assertEquals(JenkinsError.Timeout, ThrowableErrorMapper.map(SocketTimeoutException()))
        assertEquals(JenkinsError.Timeout, ThrowableErrorMapper.map(InterruptedIOException("timeout")))
        assertEquals(JenkinsError.HostUnreachable, ThrowableErrorMapper.map(UnknownHostException("jenkins")))
        assertEquals(JenkinsError.HostUnreachable, ThrowableErrorMapper.map(ConnectException("refused")))
        assertEquals(JenkinsError.TlsFailure, ThrowableErrorMapper.map(SSLHandshakeException("bad cert")))
        assertEquals(JenkinsError.HostUnreachable, ThrowableErrorMapper.map(IOException("reset")))
    }

    @Test
    fun `Android cleartext block is recognised`() {
        val e = UnknownServiceException("CLEARTEXT communication to 192.168.1.20 not permitted by network security policy")
        assertEquals(JenkinsError.CleartextBlocked, ThrowableErrorMapper.map(e))
    }

    @Test
    fun `JSON problems map to InvalidResponse even when they are IOExceptions`() {
        assertEquals(JenkinsError.InvalidResponse, ThrowableErrorMapper.map(JsonSyntaxException("Expected BEGIN_OBJECT")))
        assertEquals(JenkinsError.InvalidResponse, ThrowableErrorMapper.map(MalformedJsonException("bad")))
        assertEquals(JenkinsError.InvalidResponse, ThrowableErrorMapper.map(EOFException("End of input")))
    }

    @Test
    fun `unexpected exceptions keep only the class name, never the message`() {
        val secret = "token=11aa22bb33cc"
        val error = ThrowableErrorMapper.map(IllegalStateException("failed with $secret"))
        assertEquals(JenkinsError.Unexpected("IllegalStateException"), error)
        assertFalse(error.toString().contains(secret))
    }
}
