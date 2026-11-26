package com.jobtracker.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val QuickBreakNotes = listOf("Lunch", "Coffee", "Rest", "Personal")

/** Asks for an optional break note. Used both when starting a break and editing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakNoteDialog(
    title: String,
    initial: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What's the break for? (optional)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickBreakNotes.forEach { q ->
                        FilterChip(
                            selected = text == q,
                            onClick = { text = if (text == q) "" else q },
                            label = { Text(q) },
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Note") },
                    placeholder = { Text("e.g. Doctor's appointment") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.ifBlank { null }) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
