package com.example.jenkinslearning.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {

    /** "3m 07s", "1h 02m", "850 ms". Null or negative becomes "—". */
    fun duration(millis: Long?): String {
        if (millis == null || millis < 0) return "—"
        if (millis < 1_000) return "$millis ms"
        val totalSeconds = millis / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> "${seconds}s"
        }
    }

    private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)

    fun timestamp(epochMillis: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochMillis == null || epochMillis <= 0) return "—"
        return dateTime.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }
}
