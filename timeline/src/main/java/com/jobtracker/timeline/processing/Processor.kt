package com.jobtracker.timeline.processing

import android.content.Context
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.Place
import com.jobtracker.timeline.data.Segment
import com.jobtracker.timeline.data.SegmentType
import com.jobtracker.timeline.places.PlaceLookup
import com.jobtracker.timeline.ui.TodayWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/** Turns raw points in the database into visit/trip segments, incrementally. */
object Processor {
    private val mutex = Mutex()
    private val detector = SegmentDetector()
    private const val CHUNK = 7L * 24 * 3_600_000

    /** Re-detects the open end of the timeline (from the last automatic visit onwards). */
    suspend fun processRecent(context: Context) = mutex.withLock { run(context, full = false) }

    /** Throws away all automatic segments and rebuilds them from raw points (keeps user edits). */
    suspend fun reprocessAll(context: Context, progress: (String) -> Unit = {}) = mutex.withLock {
        TimelineApp.instance.dao.deleteAllAutoSegments()
        run(context, full = true, progress)
    }

    private suspend fun run(context: Context, full: Boolean, progress: (String) -> Unit = {}) {
        val dao = TimelineApp.instance.dao
        val lastPoint = dao.lastPointTime() ?: return
        val first = dao.firstPointTime() ?: return
        var windowStart = if (full) first else dao.lastAutoVisit()?.startTime ?: dao.lastSegment()?.endTime ?: first
        if (windowStart > lastPoint) windowStart = first
        dao.deleteAutoSegmentsFrom(windowStart)
        val places = dao.allPlaces()

        while (true) {
            val chunkEnd = windowStart + CHUNK
            val isLast = chunkEnd > lastPoint
            val to = if (isLast) lastPoint + 1 else chunkEnd
            val edited = dao.editedSegmentsBetween(windowStart, to)
            val pts = dao.pointsBetween(windowStart, to)
                .filter { p -> edited.none { p.time in it.startTime..it.endTime } }
                .map { P(it.time, it.lat, it.lon, it.accuracy, it.activity) }
            val detected = detector.detect(pts)

            val commit: List<Detected>
            var next = chunkEnd
            if (isLast) commit = detected else {
                val lastVisit = detected.indexOfLast { it.type == SegmentType.VISIT }
                if (lastVisit > 0) {
                    commit = detected.subList(0, lastVisit); next = detected[lastVisit].start
                } else commit = detected
            }
            dao.insertSegments(commit.map { it.toSegment(places) })
            if (isLast) break
            windowStart = next
            progress("Processed up to ${java.time.Instant.ofEpochMilli(windowStart).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}")
        }

        PlaceLookup.geocodeRecentVisits(context, limit = 15)
        applyRetention()
        TodayWidget.refresh(context)
    }

    private fun Detected.toSegment(places: List<Place>) = Segment(
        type = type, startTime = start, endTime = end, lat = lat, lon = lon, endLat = endLat, endLon = endLon,
        placeId = if (type == SegmentType.VISIT) matchPlace(places, lat, lon)?.id else null,
        mode = mode, distanceMeters = distance,
    )

    fun matchPlace(places: List<Place>, lat: Double, lon: Double): Place? =
        places.map { it to Geo.distance(it.lat, it.lon, lat, lon) }
            .filter { (p, d) -> d <= p.radius }
            .minByOrNull { it.second }?.first

    /** Points visits near a newly saved place at it (unless the user picked something else). */
    suspend fun attachVisitsToPlace(place: Place) {
        val dao = TimelineApp.instance.dao
        for (v in dao.allVisits()) {
            if (v.placeId == null && Geo.distance(v.lat, v.lon, place.lat, place.lon) <= place.radius) {
                dao.updateSegment(v.copy(placeId = place.id))
            }
        }
    }

    suspend fun applyRetention() {
        val app = TimelineApp.instance
        val months = app.settings.retentionMonths.value
        if (months <= 0) return
        val cutoff = ZonedDateTime.now().minusMonths(months.toLong()).toInstant().toEpochMilli()
        app.dao.deletePointsBefore(cutoff)
        app.dao.deleteSegmentsBefore(cutoff)
    }
}
