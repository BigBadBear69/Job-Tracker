package com.jobtracker.clock.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jobtracker.clock.Format
import com.jobtracker.clock.data.BreakEntry
import com.jobtracker.clock.data.Shift
import com.jobtracker.clock.data.ShiftWithBreaks
import java.util.Calendar

/** Opens a date picker followed by a time picker, starting from [initial]. */
fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(context, { _, y, m, d ->
        cal.set(y, m, d)
        TimePickerDialog(context, { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onPicked(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

private fun label(t: Long) = "${Format.date(t)}  ${Format.time(t)}"

@Composable
fun EditShiftDialog(
    original: ShiftWithBreaks,
    onDismiss: () -> Unit,
    onSave: (shift: Shift, updated: List<BreakEntry>, added: List<BreakEntry>, deleted: List<BreakEntry>) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var shift by remember { mutableStateOf(original.shift) }
    val breaks = remember { mutableStateListOf(*original.sortedBreaks.toTypedArray()) }
    var editingNoteIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val end = shift.endTime
    val error = when {
        end != null && end <= shift.startTime -> "Clock-out must be after clock-in."
        breaks.any { it.startTime < shift.startTime || (end != null && (it.endTime ?: end) > end) } ->
            "Breaks must be inside the shift."
        breaks.any { it.endTime != null && it.endTime <= it.startTime } -> "A break ends before it starts."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit shift") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("Clock in", label(shift.startTime)) {
                    pickDateTime(context, shift.startTime) { shift = shift.copy(startTime = it) }
                }
                if (end != null) {
                    TimeField("Clock out", label(end)) {
                        pickDateTime(context, end) { shift = shift.copy(endTime = it) }
                    }
                } else {
                    Text("Still clocked in", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Breaks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val start = shift.startTime + 60 * 60_000
                        breaks.add(BreakEntry(shiftId = shift.id, startTime = start, endTime = start + 30 * 60_000))
                    }) {
                        Icon(Icons.Filled.Add, null); Text("Add")
                    }
                }
                breaks.forEachIndexed { i, b ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.note ?: "Break (no note)", modifier = Modifier.weight(1f))
                                IconButton(onClick = { editingNoteIndex = i }) { Icon(Icons.Filled.Edit, "Edit note") }
                                IconButton(onClick = { breaks.removeAt(i) }) { Icon(Icons.Filled.Delete, "Delete break") }
                            }
                            TimeField("Start", Format.time(b.startTime)) {
                                pickDateTime(context, b.startTime) { breaks[i] = breaks[i].copy(startTime = it) }
                            }
                            val be = b.endTime
                            if (be != null) {
                                TimeField("End", Format.time(be)) {
                                    pickDateTime(context, be) { breaks[i] = breaks[i].copy(endTime = it) }
                                }
                            } else {
                                Text("In progress", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text("Delete shift", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = {
                val originalIds = original.breaks.map { it.id }.toSet()
                val keptIds = breaks.map { it.id }.toSet()
                onSave(
                    shift,
                    breaks.filter { it.id != 0L && it != original.breaks.first { o -> o.id == it.id } },
                    breaks.filter { it.id == 0L },
                    original.breaks.filter { it.id in originalIds && it.id !in keptIds },
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    editingNoteIndex?.let { i ->
        BreakNoteDialog(
            title = "Break note",
            initial = breaks[i].note ?: "",
            confirmLabel = "OK",
            onDismiss = { editingNoteIndex = null },
            onConfirm = { breaks[i] = breaks[i].copy(note = it); editingNoteIndex = null },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this shift?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TimeField(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text(value) }
    }
}
