package com.jobtracker.timeline.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.io.DataTransfer
import com.jobtracker.timeline.processing.Processor
import com.jobtracker.timeline.tracking.Permissions
import com.jobtracker.timeline.tracking.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val app = TimelineApp.instance
    val s = app.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracking by s.tracking.collectAsState()
    val lookups by s.onlineLookups.collectAsState()
    val photos by s.photos.collectAsState()
    val retention by s.retentionMonths.collectAsState()

    var busy by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var rangeFrom by remember { mutableStateOf<LocalDate?>(null) }

    fun job(label: String, block: suspend ((String) -> Unit) -> String) = scope.launch {
        busy = label
        result = runCatching { withContext(Dispatchers.IO) { block { msg -> busy = msg } } }
            .getOrElse { "Failed: ${it.message ?: it.javaClass.simpleName}" }
        busy = null
    }

    val photoPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { s.setPhotos(it) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) job("Importing…") { p -> DataTransfer.import(context, uri, p) }
    }
    val gpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) job("Exporting GPX…") { DataTransfer.exportGpx(context, uri); "GPX file saved." }
    }
    val kml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { uri ->
        if (uri != null) job("Exporting KML…") { DataTransfer.exportKml(context, uri); "KML file saved." }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) job("Creating backup…") { DataTransfer.exportBackup(context, uri); "Backup saved." }
    }
    val stats by produceState("", busy) { value = "${app.dao.pointCount()} recorded locations" }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Header("Recording")
        Toggle("Record my timeline", if (tracking) "On · a notification shows while recording" else "Paused", tracking) {
            s.setTracking(it); TrackingService.sync(context)
        }
        PermissionSteps(showStart = false)

        Header("Privacy")
        Toggle(
            "Online place-name lookups",
            if (lookups) "Sends visit coordinates to the phone's geocoder and OpenStreetMap to show addresses and nearby businesses."
            else "Offline: nothing leaves your phone. Name places yourself.",
            lookups,
        ) { s.setOnlineLookups(it) }
        Toggle("Show photos on timeline", "Shows photos you took during each visit or trip.", photos) { on ->
            if (on && !Permissions.photos(context)) photoPerm.launch(Permissions.photoPermission) else s.setPhotos(on)
        }

        Header("Keep history")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Forever", 3 to "3 months", 18 to "18 months", 36 to "36 months").forEach { (m, l) ->
                FilterChip(retention == m, {
                    s.setRetentionMonths(m); if (m > 0) scope.launch { Processor.applyRetention() }
                }, label = { Text(l) })
            }
        }
        Text("Older history is deleted automatically.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Header("Import & export")
        Action("Import Google Timeline or backup", "Pick a Timeline.json / Records.json from Google, or a backup from this app.") {
            importer.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }
        Action("Export GPX", "All recorded locations as tracks (for other map apps).") { gpx.launch("timeline.gpx") }
        Action("Export KML", "Visits and trips (opens in Google Earth).") { kml.launch("timeline.kml") }
        Action("Back up", "Everything in one file you can restore later.") { backup.launch("timeline-backup.json") }

        Header("Data")
        Text(stats, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Action("Rebuild timeline", "Re-detect visits and trips from recorded locations. Your edits are kept.") {
            job("Rebuilding…") { p -> Processor.reprocessAll(context, p); "Timeline rebuilt." }
        }
        Action("Delete a date range", "Pick the first and last day to delete.") {
            pickDate(context, LocalDate.now()) { a ->
                rangeFrom = a
                pickDate(context, a) { b ->
                    val (x, y) = if (b < a) b to a else a to b
                    confirm = "Delete everything from ${Fmt.day(x)} to ${Fmt.day(y)}?" to suspend { Actions.deleteRange(x, y) }
                }
            }
        }
        Action("Delete all history", "Removes every visit, trip and location. Saved places are kept.", danger = true) {
            confirm = "Delete all timeline history?" to suspend { Actions.deleteAll() }
        }
    }

    busy?.let {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp)); Text("  $it")
            }
        })
    }
    result?.let {
        AlertDialog(onDismissRequest = { result = null }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } })
    }
    confirm?.let { (title, action) ->
        ConfirmDelete(title, "This can't be undone.", onDismiss = { confirm = null }) {
            job("Deleting…") { action(); "Deleted." }
        }
    }
}

@Composable
private fun Header(t: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Toggle(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Action(title: String, desc: String, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
