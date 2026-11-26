package com.jobtracker.timeline.ui

import com.jobtracker.timeline.data.GeocodeEntry
import com.jobtracker.timeline.data.Place
import com.jobtracker.timeline.data.Segment
import com.jobtracker.timeline.places.PlaceLookup

/** Title and subtitle for a visit: saved place name, else address, else coordinates. */
fun visitTitle(s: Segment, places: Map<Long, Place>, geo: Map<String, GeocodeEntry>): Pair<String, String?> {
    val place = s.placeId?.let(places::get)
    val g = geo[PlaceLookup.key(s.lat, s.lon)]
    return when {
        place != null -> place.name to (place.address ?: g?.address)
        g?.address != null -> g.address.substringBefore(",") to g.address.substringAfter(", ", "").ifBlank { null }
        else -> "Unnamed place" to Fmt.coords(s.lat, s.lon)
    }
}
