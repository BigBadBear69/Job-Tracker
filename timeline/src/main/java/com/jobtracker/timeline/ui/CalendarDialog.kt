package com.jobtracker.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jobtracker.timeline.TimelineApp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Month calendar; days with timeline data get a dot. */
@Composable
fun CalendarDialog(selected: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val from = Fmt.startOf(month.atDay(1)); val to = Fmt.startOf(month.plusMonths(1).atDay(1))
    val starts by remember(month) { TimelineApp.instance.dao.observeSegmentStarts(from, to) }.collectAsState(emptyList())
    val withData = remember(starts) { starts.map { Fmt.date(it) }.toSet() }
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(today) }) { Text("Today") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                    }
                }
                Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                } }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = offset + month.lengthOfMonth()
                for (week in 0 until (cells + 6) / 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (dow in 0..6) {
                            val dayNum = week * 7 + dow - offset + 1
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (dayNum in 1..month.lengthOfMonth()) {
                                    val d = month.atDay(dayNum)
                                    val isSel = d == selected
                                    val future = d.isAfter(today)
                                    Column(
                                        Modifier.size(38.dp).background(
                                            if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                            CircleShape,
                                        ).clickable(enabled = !future) { onPick(d) },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text("$dayNum",
                                            color = when { isSel -> MaterialTheme.colorScheme.onPrimary; future -> MaterialTheme.colorScheme.outline; else -> MaterialTheme.colorScheme.onSurface },
                                            fontWeight = if (d == today) FontWeight.Bold else null)
                                        if (d in withData) Box(Modifier.padding(top = 2.dp).size(4.dp).background(
                                            if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
