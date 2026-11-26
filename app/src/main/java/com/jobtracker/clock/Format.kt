package com.jobtracker.clock

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Format {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    /** 1:05:09 style, used for live timers. */
    fun clock(millis: Long): String {
        val s = millis / 1000
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** 7h 32m style, used in summaries. */
    fun hm(millis: Long): String {
        val m = millis / 60_000
        return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
    }

    fun time(epochMillis: Long): String = timeFmt.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    fun date(epochMillis: Long): String = dateFmt.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
