package com.jobtracker.clock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jobtracker.clock.ClockViewModel
import com.jobtracker.clock.CsvExport
import com.jobtracker.clock.Format
import com.jobtracker.clock.data.ShiftWithBreaks

@Composable
fun HistoryScreen(vm: ClockViewModel, modifier: Modifier = Modifier) {
    val shifts by vm.allShifts.collectAsState()
    val now by rememberNow(running = shifts.any { it.shift.endTime == null })
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ShiftWithBreaks?>(null) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { CsvExport.share(context, shifts.reversed()) }, enabled = shifts.isNotEmpty()) {
                    Icon(Icons.Filled.Share, contentDescription = "Export CSV")
                }
            }
        }
        item { TotalsCard(shifts, now) }
        if (shifts.isEmpty()) {
            item {
                Text(
                    "No shifts yet. Clock in on the Clock tab to start tracking.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        val grouped = shifts.groupBy { Format.localDate(it.shift.startTime) }
        grouped.forEach { (_, dayShifts) ->
            item {
                val dayTotal = dayShifts.sumOf { it.workedMillis(now) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(Format.date(dayShifts.first().shift.startTime), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Format.hm(dayTotal), fontWeight = FontWeight.SemiBold)
                }
            }
            items(dayShifts, key = { it.shift.id }) { s -> ShiftCard(s, now) { editing = s } }
        }
    }

    editing?.let { s ->
        EditShiftDialog(
            original = s,
            onDismiss = { editing = null },
            onSave = { shift, updated, added, deleted ->
                vm.updateShift(shift)
                updated.forEach(vm::updateBreak)
                added.forEach(vm::addBreak)
                deleted.forEach(vm::deleteBreak)
                editing = null
            },
            onDelete = { vm.deleteShift(s.shift); editing = null },
        )
    }
}

@Composable
private fun ShiftCard(s: ShiftWithBreaks, now: Long, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${Format.time(s.shift.startTime)} – ${s.shift.endTime?.let(Format::time) ?: "now"}",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                Text(Format.hm(s.workedMillis(now)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (s.breaks.isNotEmpty()) {
                Text(
                    "Breaks: ${Format.hm(s.breakMillis(now))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.sortedBreaks.forEach { b ->
                    Text(
                        "• ${b.note ?: "Break"}  ${Format.time(b.startTime)}–${b.endTime?.let(Format::time) ?: "now"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
