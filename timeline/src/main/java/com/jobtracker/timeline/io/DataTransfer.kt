package com.jobtracker.timeline.io

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.LocationPoint
import com.jobtracker.timeline.data.Place
import com.jobtracker.timeline.data.PlaceCategory
import com.jobtracker.timeline.data.Segment
import com.jobtracker.timeline.data.SegmentType
import com.jobtracker.timeline.data.TravelMode
import com.jobtracker.timeline.processing.Processor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Export (GPX, KML, JSON backup) and import (Google history or JSON backup). */
object DataTransfer {
    private const val BACKUP_FORMAT = "jobtracker-timeline-backup"
    private val iso = DateTimeFormatter.ISO_INSTANT
    private const val DAY = 24 * 3_600_000L

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Iterates all points a week at a time so huge histories don't need to fit in memory. */
    private suspend fun forEachPointChunk(block: (List<LocationPoint>) -> Unit) {
        val dao = TimelineApp.instance.dao
        var t = dao.firstPointTime() ?: return
        val last = dao.lastPointTime() ?: return
        while (t <= last) {
            block(dao.pointsBetween(t, t + 7 * DAY)); t += 7 * DAY
        }
    }

    suspend fun exportGpx(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { w ->
            w.write("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Job Tracker Timeline" xmlns="http://www.topografix.com/GPX/1/1">
""")
            var day: String? = null
            forEachPointChunk { pts ->
                for (p in pts) {
                    val d = Instant.ofEpochMilli(p.time).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    if (d != day) {
                        if (day != null) w.write("</trkseg></trk>\n")
                        w.write("<trk><name>$d</name><trkseg>\n"); day = d
                    }
                    w.write("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><time>${iso.format(Instant.ofEpochMilli(p.time))}</time></trkpt>\n")
                }
            }
            if (day != null) w.write("</trkseg></trk>\n")
            w.write("</gpx>\n")
        }
    }

    suspend fun exportKml(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val dao = TimelineApp.instance.dao
        val places = dao.allPlaces().associateBy { it.id }
        context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { w ->
            w.write("""<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>Timeline</name>
""")
            for (s in dao.allSegments()) {
                val span = "<TimeSpan><begin>${iso.format(Instant.ofEpochMilli(s.startTime))}</begin><end>${iso.format(Instant.ofEpochMilli(s.endTime))}</end></TimeSpan>"
                if (s.type == SegmentType.VISIT) {
                    val name = s.placeId?.let { places[it]?.name } ?: "Visit"
                    w.write("<Placemark><name>${esc(name)}</name>$span<Point><coordinates>${s.lon},${s.lat}</coordinates></Point></Placemark>\n")
                } else {
                    val pts = dao.pointsBetween(s.startTime, s.endTime + 1)
                    val coords = (listOf(s.lon to s.lat) + pts.map { it.lon to it.lat } + listOf(s.endLon to s.endLat))
                        .joinToString(" ") { "${it.first},${it.second}" }
                    w.write("<Placemark><name>${s.mode?.label ?: "Trip"}</name>$span<LineString><coordinates>$coords</coordinates></LineString></Placemark>\n")
                }
            }
            w.write("</Document></kml>\n")
        }
    }

    suspend fun exportBackup(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val dao = TimelineApp.instance.dao
        JsonWriter(OutputStreamWriter(context.contentResolver.openOutputStream(uri)!!)).use { w ->
            w.beginObject()
            w.name("format").value(BACKUP_FORMAT).name("version").value(1)
            w.name("places").beginArray()
            for (p in dao.allPlaces()) {
                w.beginObject().name("id").value(p.id).name("name").value(p.name).name("lat").value(p.lat)
                    .name("lon").value(p.lon).name("radius").value(p.radius).name("category").value(p.category.name)
                    .name("address").value(p.address).endObject()
            }
            w.endArray()
            w.name("editedSegments").beginArray()
            for (s in dao.allSegments().filter { it.edited }) {
                w.beginObject().name("type").value(s.type.name).name("start").value(s.startTime).name("end").value(s.endTime)
                    .name("lat").value(s.lat).name("lon").value(s.lon).name("endLat").value(s.endLat).name("endLon").value(s.endLon)
                    .name("placeId").value(s.placeId).name("mode").value(s.mode?.name).name("distance").value(s.distanceMeters)
                    .endObject()
            }
            w.endArray()
            w.name("points").beginArray()
            forEachPointChunk { pts ->
                for (p in pts) {
                    w.beginArray().value(p.time).value(p.lat).value(p.lon).value(p.accuracy.toDouble()).value(p.activity).endArray()
                }
            }
            w.endArray()
            w.endObject()
        }
    }

    /**
     * Imports a JSON file — either this app's backup or a Google location history export.
     * Returns a short summary for the user.
     */
    suspend fun import(context: Context, uri: Uri, progress: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val dao = TimelineApp.instance.dao
        val isBackup = context.contentResolver.openInputStream(uri)!!.use { s ->
            val head = ByteArray(200).let { b -> String(b, 0, s.read(b).coerceAtLeast(0)) }
            BACKUP_FORMAT in head
        }
        val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)!!), 1 shl 16)
        val count = reader.use { r ->
            if (isBackup) restoreBackup(r, progress) else GoogleImport.read(r) { batch ->
                runBlocking { dao.insertPoints(batch) }
                progress("Imported ${batch.size} more points…")
            }
        }
        progress("Building your timeline…")
        Processor.reprocessAll(context, progress)
        if (isBackup) "Restored $count points from backup." else "Imported $count location points from Google."
    }

    private suspend fun restoreBackup(reader: java.io.Reader, progress: (String) -> Unit): Int {
        val dao = TimelineApp.instance.dao
        val r = JsonReader(reader)
        val idMap = HashMap<Long, Long>()
        val pending = ArrayList<Segment>()
        var count = 0
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "places" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        val p = r.readTree()
                        val newId = dao.insertPlace(
                            Place(
                                name = p.str("name") ?: "Place", lat = p.num("lat") ?: 0.0, lon = p.num("lon") ?: 0.0,
                                radius = p.num("radius") ?: 100.0,
                                category = runCatching { PlaceCategory.valueOf(p.str("category")!!) }.getOrDefault(PlaceCategory.OTHER),
                                address = p.str("address"),
                            ),
                        )
                        p.num("id")?.let { idMap[it.toLong()] = newId }
                    }
                    r.endArray()
                }
                "editedSegments" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        val s = r.readTree()
                        pending += Segment(
                            type = SegmentType.valueOf(s.str("type") ?: "VISIT"),
                            startTime = s.num("start")?.toLong() ?: 0, endTime = s.num("end")?.toLong() ?: 0,
                            lat = s.num("lat") ?: 0.0, lon = s.num("lon") ?: 0.0,
                            endLat = s.num("endLat") ?: 0.0, endLon = s.num("endLon") ?: 0.0,
                            placeId = s.num("placeId")?.toLong(),
                            mode = s.str("mode")?.let { runCatching { TravelMode.valueOf(it) }.getOrNull() },
                            distanceMeters = s.num("distance") ?: 0.0, edited = true,
                        )
                    }
                    r.endArray()
                }
                "points" -> {
                    r.beginArray()
                    val batch = ArrayList<LocationPoint>(2000)
                    while (r.hasNext()) {
                        r.beginArray()
                        val t = r.nextLong(); val lat = r.nextDouble(); val lon = r.nextDouble(); val acc = r.nextDouble()
                        val act = if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextString()
                        r.endArray()
                        batch += LocationPoint(time = t, lat = lat, lon = lon, accuracy = acc.toFloat(), activity = act)
                        if (batch.size >= 2000) { dao.insertPoints(batch.toList()); count += batch.size; batch.clear(); progress("Restored $count points…") }
                    }
                    dao.insertPoints(batch); count += batch.size
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        dao.insertSegments(pending.map { it.copy(placeId = it.placeId?.let(idMap::get)) })
        return count
    }
}
