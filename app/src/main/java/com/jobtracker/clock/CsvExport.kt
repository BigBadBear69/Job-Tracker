package com.jobtracker.clock

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jobtracker.clock.data.ShiftWithBreaks
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExport {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun ts(millis: Long?): String =
        millis?.let { fmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: ""

    private fun esc(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    fun build(shifts: List<ShiftWithBreaks>): String {
        val now = System.currentTimeMillis()
        val sb = StringBuilder("Date,Clock In,Clock Out,Worked (h),Breaks (h),Total (h),Break Details\n")
        for (s in shifts) {
            val details = s.sortedBreaks.joinToString("; ") { b ->
                val label = b.note?.takeIf { it.isNotBlank() } ?: "Break"
                "$label ${Format.time(b.startTime)}-${b.endTime?.let(Format::time) ?: "…"}"
            }
            sb.append(ts(s.shift.startTime).substring(0, 10)).append(',')
                .append(ts(s.shift.startTime)).append(',')
                .append(ts(s.shift.endTime)).append(',')
                .append(hours(s.workedMillis(now))).append(',')
                .append(hours(s.breakMillis(now))).append(',')
                .append(hours(s.totalMillis(now))).append(',')
                .append(esc(details)).append('\n')
        }
        return sb.toString()
    }

    private fun hours(ms: Long) = String.format(java.util.Locale.US, "%.2f", ms / 3_600_000.0)

    fun share(context: Context, shifts: List<ShiftWithBreaks>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "shifts.csv")
        file.writeText(build(shifts))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Work shifts")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export shifts"))
    }
}
