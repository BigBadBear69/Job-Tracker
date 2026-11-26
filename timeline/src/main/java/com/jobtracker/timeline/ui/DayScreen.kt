package com.jobtracker.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.LocationPoint
import com.jobtracker.timeline.data.Segment
import com.jobtracker.timeline.data.SegmentType
import com.jobtracker.timeline.places.PlaceLookup
import com.jobtracker.timeline.processing.Processor
import com.jobtracker.timeline.tracking.Permissions
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun DayScreen(date: LocalDate, onDateChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val app = TimelineApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val from = Fmt.startOf(date); val to = Fmt.startOf(date.plusDays(1))

    val segments by remember(date) { app.dao.observeSegmentsBetween(from, to) }.collectAsState(initial = emptyList())
    val places by app.dao.observePlaces().collectAsState(initial = emptyList())
    val geocodes by app.dao.observeGeocodes().collectAsState(initial = emptyList())
    val photosOn by app.settings.photos.collectAsState()
    val placeMap = remember(places) { places.associateBy { it.id } }
    val geoMap = remember(geocodes) { geocodes.associateBy { it.key } }

    // Refresh processing when today is opened so the latest movement shows up.
    LaunchedEffect(date) { if (date == LocalDate.now()) runCatching { Processor.processRecent(context) } }

    val points by produceState<List<LocationPoint>>(emptyList(), date, segments) {
        value = app.dao.pointsBetween(from, to)
    }
    val photos by produceState<List<Photo>>(emptyList(), date, photosOn) {
        value = if (photosOn && Permissions.photos(context)) queryPhotos(context, from, to) else emptyList()
    }
    // Look up addresses for visits on screen (only if online lookups are on; otherwise cache only).
    LaunchedEffect(segments) {
        segments.filter { it.type == SegmentType.VISIT && it.placeId == null }.forEach { PlaceLookup.address(context, it.lat, it.lon) }
    }

    val shown = segments.filter { it.type == SegmentType.VISIT || it.distanceMeters >= 30 }
    var selected by remember(date) { mutableStateOf<Long?>(null) }
    var editVisit by remember { mutableStateOf<Segment?>(null) }
    var editTrip by remember { mutableStateOf<Segment?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteDay by remember { mutableStateOf(false) }

    val lines = remember(shown, points) {
        shown.filter { it.type == SegmentType.TRIP }.map { t ->
            val inner = points.filter { it.time in t.startTime..t.endTime && it.accuracy <= 100 }.map { it.lat to it.lon }
            MapLine(listOf(t.lat to t.lon) + inner + listOf(t.endLat to t.endLon), modeColorHex(t.mode))
        }
    }
    val pins = remember(shown, placeMap, geoMap) {
        shown.filter { it.type == SegmentType.VISIT }.map { v ->
            MapPin(v.id.toString(), v.lat, v.lon, visitTitle(v, placeMap, geoMap).first)
        }
    }
    val focus = shown.firstOrNull { it.id == selected }?.let { it.lat to it.lon }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDateChange(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
            Text(
                if (date == LocalDate.now()) "Today" else Fmt.day(date),
                Modifier.weight(1f).clickable { showCalendar = true }, style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onDateChange(date.plusDays(1)) }, enabled = date < LocalDate.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
            }
            IconButton(onClick = { showCalendar = true }) { Icon(Icons.Filled.CalendarMonth, "Calendar") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Delete this day") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; confirmDeleteDay = true })
                }
            }
        }

        TimelineMap(
            lines = lines, pins = pins,
            modifier = Modifier.fillMaxWidth().weight(0.45f),
            fitKey = Triple(date, shown.size, points.size), focus = focus,
            selectedPin = selected?.toString(),
            onPinClick = { selected = it.toLongOrNull() },
        )

        val visits = shown.filter { it.type == SegmentType.VISIT }
        val trips = shown.filter { it.type == SegmentType.TRIP }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("${visits.distinctBy { it.placeId ?: PlaceLookup.key(it.lat, it.lon) }.size}", "places")
            Stat(Fmt.distance(trips.sumOf { it.distanceMeters }), "travelled")
            Stat(Fmt.duration(trips.sumOf { it.endTime.coerceAtMost(to) - it.startTime.coerceAtLeast(from) }), "moving")
        }

        LazyColumn(Modifier.weight(0.55f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 0.dp, 12.dp, 12.dp)) {
            if (shown.isEmpty()) item {
                Text(
                    if (date == LocalDate.now()) "Nothing recorded yet today. Your visits and trips will appear here as you move around."
                    else "No timeline data for this day.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(shown, key = { it.id }) { s ->
                val segPhotos = photos.filter { it.takenAt in s.startTime..s.endTime }
                if (s.type == SegmentType.VISIT) {
                    val (title, sub) = visitTitle(s, placeMap, geoMap)
                    VisitRow(s, title, sub, categoryIcon(s.placeId?.let(placeMap::get)?.category), from, to, s.id == selected, segPhotos) {
                        selected = s.id; editVisit = s
                    }
                } else TripRow(s, s.id == selected, segPhotos) { selected = s.id; editTrip = s }
            }
        }
    }

    if (showCalendar) CalendarDialog(date, { showCalendar = false }) { onDateChange(it); showCalendar = false }
    editVisit?.let { v ->
        VisitDialog(v, visitTitle(v, placeMap, geoMap), onDismiss = { editVisit = null })
    }
    editTrip?.let { t -> TripDialog(t, onDismiss = { editTrip = null }) }
    if (confirmDeleteDay) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDay = false },
            title = { Text("Delete ${Fmt.day(date)}?") },
            text = { Text("All visits, trips and recorded locations for this day will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { scope.launch { Actions.deleteRange(date, date) }; confirmDeleteDay = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDeleteDay = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun timeRange(s: Segment, dayFrom: Long, dayTo: Long): String {
    val a = if (s.startTime < dayFrom) "${Fmt.shortDay(Fmt.date(s.startTime))} ${Fmt.time(s.startTime)}" else Fmt.time(s.startTime)
    val b = if (s.endTime >= dayTo) "${Fmt.shortDay(Fmt.date(s.endTime))} ${Fmt.time(s.endTime)}" else Fmt.time(s.endTime)
    return "$a – $b"
}

@Composable
private fun VisitRow(
    s: Segment, title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector,
    dayFrom: Long, dayTo: Long, selected: Boolean, photos: List<Photo>, onClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        colors = if (selected) androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else androidx.compose.material3.CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color(android.graphics.Color.parseColor(VISIT_COLOR)), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text("${timeRange(s, dayFrom, dayTo)} · ${Fmt.duration(s.endTime - s.startTime)}", style = MaterialTheme.typography.bodySmall)
                if (photos.isNotEmpty()) { Spacer(Modifier.height(4.dp)); PhotoStrip(photos) }
            }
        }
    }
}

@Composable
private fun TripRow(s: Segment, selected: Boolean, photos: List<Photo>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(4.dp).height(44.dp).background(modeColor(s.mode), RoundedCornerShape(2.dp)))
        }
        Icon(modeIcon(s.mode), null, tint = modeColor(s.mode), modifier = Modifier.padding(start = 12.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${s.mode?.label ?: "Moving"} · ${Fmt.distance(s.distanceMeters)}", fontWeight = FontWeight.Medium)
            Text("${Fmt.time(s.startTime)} – ${Fmt.time(s.endTime)} · ${Fmt.duration(s.endTime - s.startTime)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (photos.isNotEmpty()) { Spacer(Modifier.height(4.dp)); PhotoStrip(photos) }
        }
    }
}
