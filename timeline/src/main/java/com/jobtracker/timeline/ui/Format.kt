package com.jobtracker.timeline.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Fmt {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    private val shortDayFmt = DateTimeFormatter.ofPattern("MMM d")

    /** Countries that use miles on road signs. */
    private val imperial = Locale.getDefault().country in setOf("US", "GB", "LR", "MM")

    fun time(t: Long): String = timeFmt.format(Instant.ofEpochMilli(t).atZone(zone))
    fun day(d: LocalDate): String = dayFmt.format(d)
    fun shortDay(d: LocalDate): String = shortDayFmt.format(d)
    fun date(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    fun startOf(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun duration(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(0)
        return when {
            m < 60 -> "$m min"
            m < 24 * 60 -> if (m % 60 == 0L) "${m / 60} hr" else "${m / 60} hr ${m % 60} min"
            else -> "${m / (24 * 60)} d ${(m / 60) % 24} hr"
        }
    }

    fun distance(meters: Double): String = if (imperial) {
        val mi = meters / 1609.344
        if (mi < 0.1) "${(meters * 3.28084).toInt()} ft" else String.format(Locale.US, if (mi < 10) "%.1f mi" else "%.0f mi", mi)
    } else {
        if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.US, if (meters < 10_000) "%.1f km" else "%.0f km", meters / 1000)
    }

    fun coords(lat: Double, lon: Double) = String.format(Locale.US, "%.5f, %.5f", lat, lon)
}
