package com.example.jenkinslearning.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class TimeFormatTest {

    @Test
    fun `durations are human readable`() {
        assertEquals("—", TimeFormat.duration(null))
        assertEquals("—", TimeFormat.duration(-1))
        assertEquals("850 ms", TimeFormat.duration(850))
        assertEquals("42s", TimeFormat.duration(42_000))
        assertEquals("4m 12s", TimeFormat.duration(252_000))
        assertEquals("1h 02m", TimeFormat.duration(3_720_000))
    }

    @Test
    fun `timestamps format in the given zone`() {
        assertEquals("9 Oct 2025, 08:53", TimeFormat.timestamp(1_759_999_980_000, ZoneOffset.UTC))
        assertEquals("—", TimeFormat.timestamp(null))
        assertEquals("—", TimeFormat.timestamp(0))
    }
}
