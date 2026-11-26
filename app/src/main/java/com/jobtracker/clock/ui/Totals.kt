package com.jobtracker.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jobtracker.clock.Format
import com.jobtracker.clock.data.ShiftWithBreaks
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class Totals(val today: Long, val week: Long, val lastWeek: Long)

fun computeTotals(shifts: List<ShiftWithBreaks>, now: Long): Totals {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastWeekStart = weekStart.minusWeeks(1)
    var t = 0L; var w = 0L; var lw = 0L
    for (s in shifts) {
        val d = Format.localDate(s.shift.startTime)
        val worked = s.workedMillis(now)
        if (d == today) t += worked
        if (!d.isBefore(weekStart)) w += worked
        else if (!d.isBefore(lastWeekStart)) lw += worked
    }
    return Totals(t, w, lw)
}

@Composable
fun TotalsCard(shifts: List<ShiftWithBreaks>, now: Long) {
    val totals = computeTotals(shifts, now)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Today", Format.hm(totals.today))
            Stat("This week", Format.hm(totals.week))
            Stat("Last week", Format.hm(totals.lastWeek))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
