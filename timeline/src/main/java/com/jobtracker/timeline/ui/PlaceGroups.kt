package com.jobtracker.timeline.ui

import com.jobtracker.timeline.data.GeocodeEntry
import com.jobtracker.timeline.data.Place
import com.jobtracker.timeline.data.Segment
import com.jobtracker.timeline.places.PlaceLookup
import java.util.Locale

/** All visits to one place: a saved place, or an unsaved spot (~100 m grid cell). */
data class PlaceGroup(
    val key: String,
    val name: String,
    val subtitle: String?,
    val lat: Double,
    val lon: Double,
    val place: Place?,
    val visits: List<Segment>,
    val city: String?,
    val country: String?,
) {
    val totalMillis get() = visits.sumOf { it.endTime - it.startTime }
    val lastVisit get() = visits.maxOf { it.startTime }
}

fun groupVisits(visits: List<Segment>, places: Map<Long, Place>, geo: Map<String, GeocodeEntry>): List<PlaceGroup> =
    visits.groupBy { v ->
        v.placeId?.takeIf { places.containsKey(it) }?.let { "p:$it" }
            ?: String.format(Locale.US, "g:%.3f,%.3f", v.lat, v.lon)
    }.map { (key, vs) ->
        val first = vs.first()
        val (title, sub) = visitTitle(first, places, geo)
        val g = vs.firstNotNullOfOrNull { geo[PlaceLookup.key(it.lat, it.lon)] }
        val place = first.placeId?.let(places::get)
        PlaceGroup(key, title, sub, place?.lat ?: first.lat, place?.lon ?: first.lon, place, vs, g?.city, g?.country)
    }
