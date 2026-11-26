package com.jobtracker.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.GeocodeEntry
import com.jobtracker.timeline.data.Place
import com.jobtracker.timeline.data.PlaceCategory
import com.jobtracker.timeline.data.Segment
import com.jobtracker.timeline.data.SegmentType
import com.jobtracker.timeline.places.PlaceLookup
import com.jobtracker.timeline.processing.Geo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TravelTrip(val start: LocalDate, val end: LocalDate, val title: String, val places: Int, val farthest: Double)

/**
 * Multi-day trips: runs of visits more than 50 km from Home that include at least one night away.
 */
fun detectTrips(visits: List<Segment>, home: Place?, geo: Map<String, GeocodeEntry>): List<TravelTrip> {
    if (home == null) return emptyList()
    val out = mutableListOf<TravelTrip>()
    var run = mutableListOf<Segment>()
    fun flush() {
        if (run.isNotEmpty()) {
            val s = Fmt.date(run.first().startTime); val e = Fmt.date(run.last().endTime)
            if (e.isAfter(s)) {
                val cities = run.mapNotNull { geo[PlaceLookup.key(it.lat, it.lon)]?.city }
                val city = cities.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val far = run.maxOf { Geo.distance(home.lat, home.lon, it.lat, it.lon) }
                out += TravelTrip(s, e, city?.let { "Trip to $it" } ?: "Trip · ${Fmt.distance(far)} from home",
                    run.distinctBy { PlaceLookup.key(it.lat, it.lon) }.size, far)
            }
        }
        run = mutableListOf()
    }
    for (v in visits.sortedBy { it.startTime }) {
        if (Geo.distance(home.lat, home.lon, v.lat, v.lon) > 50_000) run += v else flush()
    }
    flush()
    return out.reversed()
}

@Composable
fun InsightsScreen(onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val app = TimelineApp.instance
    var yearly by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    val start = if (yearly) anchor.withDayOfYear(1) else anchor.withDayOfMonth(1)
    val end = if (yearly) start.plusYears(1) else start.plusMonths(1)
    val places by app.dao.observePlaces().collectAsState(initial = emptyList())
    val geocodes by app.dao.observeGeocodes().collectAsState(initial = emptyList())
    val allVisits by app.dao.observeAllVisits().collectAsState(initial = emptyList())
    val segs by produceState<List<Segment>>(emptyList(), start, end, allVisits) {
        value = app.dao.segmentsBetween(Fmt.startOf(start), Fmt.startOf(end))
    }
    val placeMap = places.associateBy { it.id }
    val geoMap = remember(geocodes) { geocodes.associateBy { it.key } }
    val trips = segs.filter { it.type == SegmentType.TRIP }
    val visits = segs.filter { it.type == SegmentType.VISIT }
    val groups = remember(visits, places, geocodes) { groupVisits(visits, placeMap, geoMap) }
    val home = places.firstOrNull { it.category == PlaceCategory.HOME }
    val travel = remember(allVisits, home, geocodes) { detectTrips(allVisits, home, geoMap) }
        .filter { !it.end.isBefore(start) && it.start.isBefore(end) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!yearly, { yearly = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Month") }
            SegmentedButton(yearly, { yearly = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Year") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { anchor = if (yearly) anchor.minusYears(1) else anchor.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous")
            }
            Text(start.format(DateTimeFormatter.ofPattern(if (yearly) "yyyy" else "MMMM yyyy")), Modifier.weight(1f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { anchor = if (yearly) anchor.plusYears(1) else anchor.plusMonths(1) },
                enabled = end.isBefore(LocalDate.now()) || end == LocalDate.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Big(Fmt.distance(trips.sumOf { it.distanceMeters }), "travelled")
                Big("${groups.size}", "places")
                Big("${groups.mapNotNull { it.city }.toSet().size}", "cities")
                Big("${groups.mapNotNull { it.country }.toSet().size}", "countries")
            }
        }

        Section("By travel mode") {
            val byMode = trips.groupBy { it.mode }.mapValues { (_, l) -> l.sumOf { it.distanceMeters } to l.sumOf { it.endTime - it.startTime } }
                .entries.sortedByDescending { it.value.first }
            val maxD = byMode.maxOfOrNull { it.value.first }?.coerceAtLeast(1.0) ?: 1.0
            if (byMode.isEmpty()) Empty()
            byMode.forEach { (m, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(modeIcon(m), null, tint = modeColor(m))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("${m?.label ?: "Moving"} · ${Fmt.distance(v.first)} · ${Fmt.duration(v.second)}", style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                            Box(Modifier.fillMaxWidth((v.first / maxD).toFloat()).fillMaxHeight().background(modeColor(m), RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }

        Section("Where you spent the most time") {
            val top = groups.sortedByDescending { it.totalMillis }.take(8)
            if (top.isEmpty()) Empty()
            top.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(g.place?.category), null, tint = MaterialTheme.colorScheme.primary)
                    Text(g.name, Modifier.padding(start = 10.dp).weight(1f), maxLines = 1)
                    Text("${Fmt.duration(g.totalMillis)} · ${g.visits.size}×", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val cities = groups.filter { it.city != null }.groupBy { it.city!! to it.country }
            .mapValues { (_, gs) -> gs.sumOf { it.visits.size } }.entries.sortedByDescending { it.value }
        Section("Cities & countries") {
            if (cities.isEmpty()) Text(
                if (app.settings.onlineLookups.value) "No city info yet." else "City names need online place lookups (Settings).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            cities.take(12).forEach { (k, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(listOfNotNull(k.first, k.second).joinToString(", "), Modifier.weight(1f))
                    Text("$n visits", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Section("Trips") {
            when {
                home == null -> Text("Name a place as Home (Places tab) to detect trips away from home.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                travel.isEmpty() -> Empty()
            }
            travel.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { onOpenDay(t.start) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Luggage, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(t.title, fontWeight = FontWeight.Medium)
                        Text("${Fmt.shortDay(t.start)} – ${Fmt.shortDay(t.end)} · ${t.places} places",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Big(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Empty() = Text("Nothing for this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}
