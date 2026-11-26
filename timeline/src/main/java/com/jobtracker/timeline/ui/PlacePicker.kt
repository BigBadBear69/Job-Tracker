package com.jobtracker.timeline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.Place
import com.jobtracker.timeline.data.PlaceCategory
import com.jobtracker.timeline.places.NearbyPlace
import com.jobtracker.timeline.places.PlaceLookup
import com.jobtracker.timeline.processing.Geo
import kotlinx.coroutines.launch

/**
 * Lets the user say where a visit was: a saved place, a nearby business (online lookups), or a
 * brand-new named place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacePickerDialog(
    lat: Double,
    lon: Double,
    address: String?,
    onDismiss: () -> Unit,
    onPicked: (Place) -> Unit,
) {
    val app = TimelineApp.instance
    val places by app.dao.observePlaces().collectAsState(initial = emptyList())
    val online by app.settings.onlineLookups.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PlaceCategory.OTHER) }

    val nearby by produceState<List<NearbyPlace>?>(initialValue = null, online) {
        value = if (online) PlaceLookup.nearby(lat, lon) else emptyList()
    }

    fun create(name: String, cat: PlaceCategory, addr: String?) = scope.launch {
        onPicked(Actions.createPlace(name, cat, lat, lon, addr))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where were you?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search or name a new place") }, modifier = Modifier.fillMaxWidth(),
                )
                if (query.isNotBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlaceCategory.entries.forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { create(query, category, address) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text("  Save as new place \"${query.trim()}\"", color = MaterialTheme.colorScheme.primary)
                    }
                }
                val q = query.trim().lowercase()
                val saved = places.filter { q.isEmpty() || q in it.name.lowercase() }
                    .sortedBy { Geo.distance(it.lat, it.lon, lat, lon) }
                val near = nearby.orEmpty().filter { q.isEmpty() || q in it.name.lowercase() }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    if (saved.isNotEmpty()) item { Header("Your places") }
                    items(saved, key = { "p${it.id}" }) { p ->
                        Option(categoryIcon(p.category), p.name, Fmt.distance(Geo.distance(p.lat, p.lon, lat, lon)) + " away") {
                            onPicked(p)
                        }
                    }
                    if (online) {
                        item { Header("Nearby") }
                        if (nearby == null) item {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("  Looking up nearby places…")
                            }
                        } else if (near.isEmpty()) item {
                            Text("Nothing found nearby.", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(near, key = { "n${it.name}" }) { n ->
                            Option(Icons.Filled.Storefront, n.name, listOf(n.type, Fmt.distance(n.distance)).filter { it.isNotBlank() }.joinToString(" · ")) {
                                create(n.name, PlaceCategory.OTHER, address)
                            }
                        }
                    } else item {
                        Text(
                            "Nearby suggestions are off (offline mode). Type a name above to save this place.",
                            Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
