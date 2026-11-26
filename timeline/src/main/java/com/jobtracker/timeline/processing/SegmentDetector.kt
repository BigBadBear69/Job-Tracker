package com.jobtracker.timeline.processing

import com.jobtracker.timeline.data.SegmentType
import com.jobtracker.timeline.data.TravelMode

/** Minimal point shape used by the detector (keeps it free of Android/Room for unit tests). */
data class P(val time: Long, val lat: Double, val lon: Double, val accuracy: Float, val activity: String? = null)

data class Detected(
    val type: SegmentType,
    val start: Long,
    val end: Long,
    val lat: Double,
    val lon: Double,
    val endLat: Double,
    val endLon: Double,
    val mode: TravelMode? = null,
    val distance: Double = 0.0,
)

/**
 * Turns a time-ordered list of location fixes into visits and trips.
 *
 * Visits are found with a stay-point algorithm: a run of fixes that stay within [stayRadius]
 * metres of their running centre for at least [minStayMillis]. A couple of stray fixes
 * (GPS jumps) are tolerated. Everything between visits becomes a trip.
 */
class SegmentDetector(
    private val stayRadius: Double = 100.0,
    private val minStayMillis: Long = 5 * 60_000L,
    private val maxAccuracy: Float = 200f,
    private val mergeDistance: Double = 150.0,
    private val mergeGapMillis: Long = 30 * 60_000L,
) {
    fun detect(input: List<P>): List<Detected> {
        val pts = input.filter { it.accuracy <= maxAccuracy }.sortedBy { it.time }
        if (pts.isEmpty()) return emptyList()

        // 1. find stays as index ranges
        data class Stay(val from: Int, val to: Int, val lat: Double, val lon: Double)
        val stays = mutableListOf<Stay>()
        var i = 0
        while (i < pts.size) {
            var cLat = pts[i].lat; var cLon = pts[i].lon; var n = 1
            var lastIn = i
            var outliers = 0
            var k = i + 1
            while (k < pts.size) {
                val d = Geo.distance(cLat, cLon, pts[k].lat, pts[k].lon)
                if (d <= stayRadius) {
                    n++; cLat += (pts[k].lat - cLat) / n; cLon += (pts[k].lon - cLon) / n
                    lastIn = k; outliers = 0
                } else if (++outliers > 2) break
                k++
            }
            if (pts[lastIn].time - pts[i].time >= minStayMillis) {
                stays += Stay(i, lastIn, cLat, cLon)
                i = lastIn + 1
            } else i++
        }

        // 2. merge stays that are basically the same place with only a short hop between
        val merged = mutableListOf<Stay>()
        for (s in stays) {
            val prev = merged.lastOrNull()
            if (prev != null &&
                Geo.distance(prev.lat, prev.lon, s.lat, s.lon) <= mergeDistance &&
                pts[s.from].time - pts[prev.to].time <= mergeGapMillis &&
                pathLength(pts, prev.to, s.from) <= mergeDistance * 3
            ) {
                val a = (prev.to - prev.from + 1).toDouble(); val b = (s.to - s.from + 1).toDouble()
                merged[merged.lastIndex] = Stay(
                    prev.from, s.to,
                    (prev.lat * a + s.lat * b) / (a + b), (prev.lon * a + s.lon * b) / (a + b),
                )
            } else merged += s
        }

        // 3. emit visits and the trips between them
        val out = mutableListOf<Detected>()
        var cursor = 0
        for (s in merged) {
            if (s.from > cursor) addTrip(out, pts, cursor, s.from)
            out += Detected(SegmentType.VISIT, pts[s.from].time, pts[s.to].time, s.lat, s.lon, s.lat, s.lon)
            cursor = s.to
        }
        if (cursor < pts.lastIndex) addTrip(out, pts, cursor, pts.lastIndex)
        return out
    }

    // Adds a trip covering pts from..to; endpoints are shared with neighbouring visits.
    private fun addTrip(out: MutableList<Detected>, pts: List<P>, from: Int, to: Int) {
        if (to <= from) return
        val a = pts[from]; val b = pts[to]
        if (b.time <= a.time) return
        val dist = pathLength(pts, from, to)
        // Use the visit centre as the start/end point so lines connect to visit pins.
        val startLat = (out.lastOrNull()?.takeIf { it.type == SegmentType.VISIT }?.lat) ?: a.lat
        val startLon = (out.lastOrNull()?.takeIf { it.type == SegmentType.VISIT }?.lon) ?: a.lon
        out += Detected(
            SegmentType.TRIP, a.time, b.time, startLat, startLon, b.lat, b.lon,
            mode = guessMode(pts.subList(from, to + 1), dist, b.time - a.time),
            distance = dist,
        )
    }

    companion object {
        /** Path length that ignores jitter smaller than the fixes' accuracy. */
        fun pathLength(pts: List<P>, from: Int, to: Int): Double {
            var total = 0.0
            var last = pts[from]
            for (k in from + 1..to) {
                val p = pts[k]
                val d = Geo.distance(last.lat, last.lon, p.lat, p.lon)
                if (d > minOf(p.accuracy, 50f) || k == to) { total += d; last = p }
            }
            return total
        }

        fun guessMode(pts: List<P>, distance: Double, durationMs: Long): TravelMode {
            val kmh = if (durationMs > 0) distance / 1000 / (durationMs / 3_600_000.0) else 0.0
            if (kmh > 250 || (distance > 100_000 && kmh > 180)) return TravelMode.FLYING
            val counts = pts.mapNotNull { it.activity }.filter { it != "STILL" && it != "UNKNOWN" && it != "TILTING" }
                .groupingBy { it }.eachCount()
            val top = counts.maxByOrNull { it.value }?.key
            return when (top) {
                "IN_VEHICLE" -> TravelMode.DRIVING
                "ON_BICYCLE" -> TravelMode.CYCLING
                "RUNNING" -> TravelMode.RUNNING
                "WALKING", "ON_FOOT" -> if (kmh > 12) TravelMode.DRIVING else TravelMode.WALKING
                else -> when {
                    distance < 50 -> TravelMode.UNKNOWN
                    kmh < 7 -> TravelMode.WALKING
                    kmh < 12 -> TravelMode.RUNNING
                    else -> TravelMode.DRIVING
                }
            }
        }
    }
}
