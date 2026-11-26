package com.jobtracker.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jobtracker.timeline.data.Segment
import com.jobtracker.timeline.data.TravelMode
import kotlinx.coroutines.launch

@Composable
fun VisitDialog(visit: Segment, title: Pair<String, String?>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(visit.startTime) }
    var end by remember { mutableStateOf(visit.endTime) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val changed = start != visit.startTime || end != visit.endTime

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title.first) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                title.second?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${Fmt.day(Fmt.date(start))} · ${Fmt.duration(end - start)}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Filled.EditLocationAlt, null); Text("  Change place")
                }
                HorizontalDivider()
                TimeRow("Arrived", start) { pickDateTime(context, start) { start = it } }
                TimeRow("Left", end) { pickDateTime(context, end) { end = it } }
                if (end <= start) Text("Leaving time must be after arrival.", color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text("  Delete this visit", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = changed && end > start, onClick = {
                scope.launch { Actions.updateSegment(visit.copy(startTime = start, endTime = end)) }; onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    if (picking) PlacePickerDialog(visit.lat, visit.lon, title.second, onDismiss = { picking = false }) { place ->
        scope.launch { Actions.setVisitPlace(visit.copy(startTime = start, endTime = end), place) }
        picking = false; onDismiss()
    }
    if (confirmDelete) ConfirmDelete("Delete this visit?", "The visit and the locations recorded during it will be deleted.",
        onDismiss = { confirmDelete = false }) { scope.launch { Actions.deleteSegment(visit) }; onDismiss() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripDialog(trip: Segment, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(trip.mode ?: TravelMode.UNKNOWN) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Fmt.distance(trip.distanceMeters)} · ${Fmt.duration(trip.endTime - trip.startTime)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${Fmt.time(trip.startTime)} – ${Fmt.time(trip.endTime)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("How did you travel?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TravelMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m },
                            leadingIcon = { Icon(modeIcon(m), null, tint = modeColor(m)) }, label = { Text(m.label) })
                    }
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text("  Delete this trip", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = mode != trip.mode, onClick = {
                scope.launch { Actions.updateSegment(trip.copy(mode = mode)) }; onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (confirmDelete) ConfirmDelete("Delete this trip?", "The trip and its route will be deleted.",
        onDismiss = { confirmDelete = false }) { scope.launch { Actions.deleteSegment(trip) }; onDismiss() }
}

@Composable
private fun TimeRow(label: String, time: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text("${Fmt.shortDay(Fmt.date(time))}, ${Fmt.time(time)}") }
    }
}

@Composable
fun ConfirmDelete(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
