package com.jobtracker.timeline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.PlaceCategory
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class PlaceSort(val label: String) { RECENT("Recent"), MOST("Most visited"), TIME("Most time"), SAVED("Saved") }

/** Every place you've been, searchable, with a map of all of them. */
@Composable
fun PlacesScreen(onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val app = TimelineApp.instance
    val visits by app.dao.observeAllVisits().collectAsState(initial = emptyList())
    val places by app.dao.observePlaces().collectAsState(initial = emptyList())
    val geocodes by app.dao.observeGeocodes().collectAsState(initial = emptyList())
    val groups = remember(visits, places, geocodes) {
        groupVisits(visits, places.associateBy { it.id }, geocodes.associateBy { it.key })
    }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(PlaceSort.RECENT) }
    var open by remember { mutableStateOf<PlaceGroup?>(null) }

    val q = query.trim().lowercase()
    val filtered = groups.filter {
        q.isEmpty() || q in it.name.lowercase() || q in (it.subtitle ?: "").lowercase() || q in (it.city ?: "").lowercase()
    }.filter { sort != PlaceSort.SAVED || it.place != null }.let { l ->
        when (sort) {
            PlaceSort.RECENT, PlaceSort.SAVED -> l.sortedByDescending { it.lastVisit }
            PlaceSort.MOST -> l.sortedByDescending { it.visits.size }
            PlaceSort.TIME -> l.sortedByDescending { it.totalMillis }
        }
    }

    Column(modifier.fillMaxSize()) {
        TimelineMap(
            lines = emptyList(),
            pins = filtered.take(500).map { MapPin(it.key, it.lat, it.lon, it.name) },
            modifier = Modifier.fillMaxWidth().height(240.dp),
            fitKey = Pair(filtered.size, q),
            onPinClick = { k -> open = groups.firstOrNull { it.key == k } },
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search places, addresses, cities") },
            modifier = Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 4.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            PlaceSort.entries.forEachIndexed { i, s ->
                SegmentedButton(selected = sort == s, onClick = { sort = s },
                    shape = SegmentedButtonDefaults.itemShape(i, PlaceSort.entries.size)) { Text(s.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Text("${filtered.size} places", Modifier.padding(12.dp, 8.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.key }) { g ->
                Row(Modifier.fillMaxWidth().clickable { open = g }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(g.place?.category), null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(g.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(g.subtitle ?: g.city, "${g.visits.size} visit${if (g.visits.size == 1) "" else "s"}",
                            Fmt.duration(g.totalMillis)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(Fmt.shortDay(Fmt.date(g.lastVisit)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    open?.let { g -> PlaceDetailDialog(groups.firstOrNull { it.key == g.key } ?: g, onDismiss = { open = null }, onOpenDay = { open = null; onOpenDay(it) }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceDetailDialog(g: PlaceGroup, onDismiss: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(g.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${g.visits.size} visits · ${Fmt.duration(g.totalMillis)} total", style = MaterialTheme.typography.bodySmall)
                Row {
                    if (g.place == null) TextButton(onClick = { naming = true }) { Text("Name this place") }
                    else {
                        TextButton(onClick = { editing = true }) { Text("Edit") }
                        TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, null); Text(" Forget") }
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(g.visits.sortedByDescending { it.startTime }, key = { it.id }) { v ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenDay(Fmt.date(v.startTime)) }.padding(vertical = 8.dp)) {
                            Text(Fmt.day(Fmt.date(v.startTime)), Modifier.weight(1f))
                            Text("${Fmt.time(v.startTime)} · ${Fmt.duration(v.endTime - v.startTime)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (naming) {
        val v = g.visits.first()
        PlacePickerDialog(v.lat, v.lon, g.subtitle, onDismiss = { naming = false }) { place ->
            scope.launch { g.visits.forEach { Actions.setVisitPlace(it, place) } }
            naming = false; onDismiss()
        }
    }
    if (editing && g.place != null) {
        var name by remember { mutableStateOf(g.place.name) }
        var cat by remember { mutableStateOf(g.place.category) }
        var radius by remember { mutableStateOf(g.place.radius) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit place") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlaceCategory.entries.forEach { c -> FilterChip(cat == c, { cat = c }, label = { Text(c.label) }) }
                    }
                    Text("Recognise visits within")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50.0, 100.0, 200.0, 400.0).forEach { r -> FilterChip(radius == r, { radius = r }, label = { Text(Fmt.distance(r)) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        val updated = g.place.copy(name = name.trim(), category = cat, radius = radius)
                        TimelineApp.instance.dao.updatePlace(updated)
                        com.jobtracker.timeline.processing.Processor.attachVisitsToPlace(updated)
                    }
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && g.place != null) ConfirmDelete("Forget \"${g.place.name}\"?",
        "The saved name is removed. Your visits stay on the timeline as unnamed places.",
        onDismiss = { confirmDelete = false }) { scope.launch { Actions.deletePlace(g.place) }; onDismiss() }
}
