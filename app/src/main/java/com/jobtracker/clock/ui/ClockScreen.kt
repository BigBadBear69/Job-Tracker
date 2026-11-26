package com.jobtracker.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jobtracker.clock.ClockViewModel
import com.jobtracker.clock.Format

@Composable
fun ClockScreen(vm: ClockViewModel, modifier: Modifier = Modifier, onClockIn: () -> Unit) {
    val active by vm.activeShift.collectAsState()
    val all by vm.allShifts.collectAsState()
    val now by rememberNow(running = true)
    var showBreakDialog by remember { mutableStateOf(false) }
    var confirmClockOut by remember { mutableStateOf(false) }

    val shift = active
    val onBreak = shift?.activeBreak

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val (statusText, statusColor) = when {
            shift == null -> "Not clocked in" to MaterialTheme.colorScheme.outline
            onBreak != null -> ("On break" + (onBreak.note?.let { " · $it" } ?: "")) to BreakAmber
            else -> "Working" to WorkGreen
        }
        Text(statusText, color = statusColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        Text(
            Format.clock(shift?.workedMillis(now) ?: 0),
            fontSize = 64.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        )
        Text("time worked", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (shift != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Clocked in", Format.time(shift.shift.startTime))
                    InfoRow("Total since clock-in", Format.clock(shift.totalMillis(now)))
                    InfoRow("Break time", Format.clock(shift.breakMillis(now)))
                    if (onBreak != null) InfoRow("Current break", Format.clock(now - onBreak.startTime))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            shift == null -> BigButton("Clock In", Icons.Filled.PlayArrow, WorkGreen) {
                onClockIn(); vm.clockIn()
            }
            onBreak != null -> BigButton("End Break", Icons.Filled.PlayArrow, WorkGreen) { vm.endBreak() }
            else -> BigButton("Start Break", Icons.Filled.Coffee, BreakAmber) { showBreakDialog = true }
        }
        if (shift != null) {
            OutlinedButton(
                onClick = { confirmClockOut = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StopRed),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.size(8.dp))
                Text("Clock Out", fontSize = 18.sp)
            }
        }

        if (shift != null && shift.breaks.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Breaks this shift", style = MaterialTheme.typography.titleSmall)
                    shift.sortedBreaks.forEach { b ->
                        val end = b.endTime
                        InfoRow(
                            "${b.note ?: "Break"} · ${Format.time(b.startTime)}–${end?.let(Format::time) ?: "now"}",
                            Format.hm((end ?: now) - b.startTime),
                        )
                    }
                }
            }
        }

        TotalsCard(all, now)
    }

    if (showBreakDialog) {
        BreakNoteDialog(
            title = "Start break",
            confirmLabel = "Start break",
            onDismiss = { showBreakDialog = false },
            onConfirm = { vm.startBreak(it); showBreakDialog = false },
        )
    }
    if (confirmClockOut) {
        AlertDialog(
            onDismissRequest = { confirmClockOut = false },
            title = { Text("Clock out?") },
            text = { Text("You worked ${Format.hm(shift?.workedMillis(now) ?: 0)} this shift.") },
            confirmButton = { TextButton(onClick = { vm.clockOut(); confirmClockOut = false }) { Text("Clock out") } },
            dismissButton = { TextButton(onClick = { confirmClockOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BigButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(200.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(48.dp))
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
