package com.jobtracker.timeline.io

import android.util.JsonReader
import android.util.JsonToken
import com.jobtracker.timeline.data.LocationPoint
import java.io.Reader
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Imports Google location history. Supports:
 *  - the on-device Google Maps export ("Timeline.json": semanticSegments / rawSignals),
 *  - the older phone export (a top-level array of visit/activity/timelinePath entries),
 *  - Google Takeout "Records.json" (locations array).
 * Everything is converted to raw points; visits and trips are then re-detected by the app.
 */
object GoogleImport {
    private val numberRegex = Regex("-?\\d+(?:\\.\\d+)?")

    /** "47.61°, -122.33°" or "geo:47.61,-122.33" → pair. */
    fun parseLatLng(s: String?): Pair<Double, Double>? {
        if (s == null) return null
        val n = numberRegex.findAll(s).map { it.value.toDouble() }.toList()
        return if (n.size >= 2) n[0] to n[1] else null
    }

    fun parseTime(s: String?): Long? {
        if (s == null) return null
        s.toLongOrNull()?.let { return it }
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
    }

    /** Maps Google activity names to the labels the app's detector understands. */
    fun activityLabel(type: String?): String? {
        val t = type?.uppercase() ?: return null
        return when {
            "VEHICLE" in t || "CAR" in t || "BUS" in t || "TRAIN" in t || "TRAM" in t || "SUBWAY" in t ||
                "MOTORCYCL" in t || "TAXI" in t || "DRIVING" in t || "FERRY" in t -> "IN_VEHICLE"
            "CYCL" in t || "BICYCLE" in t -> "ON_BICYCLE"
            "RUN" in t -> "RUNNING"
            "WALK" in t || "FOOT" in t || "HIK" in t -> "WALKING"
            "FLY" in t -> null
            else -> null
        }
    }

    /** Streams the file and calls [emit] with batches of points. Returns the number imported. */
    fun read(reader: Reader, emit: (List<LocationPoint>) -> Unit): Int {
        val r = JsonReader(reader)
        r.isLenient = true
        val batch = ArrayList<LocationPoint>(2000)
        var count = 0
        fun add(t: Long?, ll: Pair<Double, Double>?, acc: Float = 50f, activity: String? = null) {
            if (t == null || ll == null) return
            batch += LocationPoint(time = t, lat = ll.first, lon = ll.second, accuracy = acc, activity = activity)
            count++
            if (batch.size >= 2000) { emit(batch.toList()); batch.clear() }
        }

        fun handleSegment(e: Any?) {
            val start = parseTime(e.str("startTime"))
            val end = parseTime(e.str("endTime"))
            e.obj("visit")?.let { v ->
                val cand = v.obj("topCandidate")
                val ll = parseLatLng(cand.obj("placeLocation").str("latLng")) ?: parseLatLng(cand.str("placeLocation"))
                add(start, ll, 30f, "STILL"); add(end, ll, 30f, "STILL")
            }
            e.obj("activity")?.let { a ->
                val label = activityLabel(a.obj("topCandidate").str("type"))
                val s = parseLatLng(a.obj("start").str("latLng")) ?: parseLatLng(a.str("start"))
                val en = parseLatLng(a.obj("end").str("latLng")) ?: parseLatLng(a.str("end"))
                add(start, s, 30f, label); add(end, en, 30f, label)
            }
            e.list("timelinePath")?.forEach { p ->
                val ll = parseLatLng(p.str("point"))
                val t = parseTime(p.str("time"))
                    ?: start?.let { st -> p.num("durationMinutesOffsetFromStartTime")?.let { st + (it * 60_000).toLong() } }
                add(t, ll, 40f)
            }
        }

        when (r.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray(); while (r.hasNext()) handleSegment(r.readTree()); r.endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "semanticSegments" -> { r.beginArray(); while (r.hasNext()) handleSegment(r.readTree()); r.endArray() }
                        "rawSignals" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                val pos = r.readTree().obj("position") ?: continue
                                add(parseTime(pos.str("timestamp")), parseLatLng(pos.str("LatLng") ?: pos.str("latLng")),
                                    pos.num("accuracyMeters")?.toFloat() ?: 50f)
                            }
                            r.endArray()
                        }
                        "locations" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                val l = r.readTree()
                                val lat = l.num("latitudeE7"); val lon = l.num("longitudeE7")
                                val t = parseTime(l.str("timestamp")) ?: l.str("timestampMs")?.toLongOrNull()
                                val act = (l.list("activity")?.firstOrNull().list("activity")?.firstOrNull()).str("type")
                                if (lat != null && lon != null) {
                                    add(t, lat / 1e7 to lon / 1e7, l.num("accuracy")?.toFloat() ?: 50f, activityLabel(act))
                                }
                            }
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
            else -> throw IllegalArgumentException("Not a location history file")
        }
        if (batch.isNotEmpty()) emit(batch.toList())
        return count
    }
}
