package com.jobtracker.timeline

import com.jobtracker.timeline.data.SegmentType
import com.jobtracker.timeline.data.TravelMode
import com.jobtracker.timeline.io.GoogleImport
import com.jobtracker.timeline.processing.Geo
import com.jobtracker.timeline.processing.P
import com.jobtracker.timeline.processing.SegmentDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DetectorTest {
    private val min = 60_000L
    private val rnd = Random(1)

    /** Fixes jittering ~20 m around a spot, one every [step]. */
    private fun stay(lat: Double, lon: Double, from: Long, minutes: Int, step: Long = 2 * min) =
        (0..(minutes * min / step).toInt()).map {
            P(from + it * step, lat + (rnd.nextDouble() - 0.5) * 0.0003, lon + (rnd.nextDouble() - 0.5) * 0.0003, 15f)
        }

    /** Straight-line movement sampled every 10 s. */
    private fun move(aLat: Double, aLon: Double, bLat: Double, bLon: Double, from: Long, minutes: Int, act: String? = null): List<P> {
        val n = minutes * 6
        return (1 until n).map { i ->
            val f = i.toDouble() / n
            P(from + i * 10_000L, aLat + (bLat - aLat) * f, aLon + (bLon - aLon) * f, 10f, act)
        }
    }

    private val home = 40.0 to -75.0
    private val work = 40.05 to -75.0   // ~5.6 km north

    @Test fun homeDriveWorkDetectsTwoVisitsAndADrive() {
        val pts = stay(home.first, home.second, 0, 60) +
            move(home.first, home.second, work.first, work.second, 60 * min, 12, "IN_VEHICLE") +
            stay(work.first, work.second, 72 * min, 120)
        val segs = SegmentDetector().detect(pts)
        assertEquals(listOf(SegmentType.VISIT, SegmentType.TRIP, SegmentType.VISIT), segs.map { it.type })
        val trip = segs[1]
        assertEquals(TravelMode.DRIVING, trip.mode)
        assertTrue("distance ${trip.distance}", trip.distance in 5000.0..6500.0)
        assertTrue(Geo.distance(segs[0].lat, segs[0].lon, home.first, home.second) < 30)
        assertTrue(Geo.distance(segs[2].lat, segs[2].lon, work.first, work.second) < 30)
    }

    @Test fun gpsJumpDoesNotSplitAVisit() {
        val a = stay(home.first, home.second, 0, 30)
        val jump = listOf(P(31 * min, home.first + 0.01, home.second, 30f)) // 1 km outlier
        val b = stay(home.first, home.second, 32 * min, 30)
        val segs = SegmentDetector().detect(a + jump + b)
        assertEquals(1, segs.count { it.type == SegmentType.VISIT })
    }

    @Test fun walkingSpeedWithoutActivityIsWalking() {
        // 1 km in 12 minutes = 5 km/h
        val dest = home.first + 0.009 to home.second
        val pts = stay(home.first, home.second, 0, 20) +
            move(home.first, home.second, dest.first, dest.second, 20 * min, 12) +
            stay(dest.first, dest.second, 32 * min, 20)
        assertEquals(TravelMode.WALKING, SegmentDetector().detect(pts).first { it.type == SegmentType.TRIP }.mode)
    }

    @Test fun shortStopIsNotAVisit() {
        val pts = move(home.first, home.second, work.first, work.second, 0, 6) +
            stay(work.first, work.second, 6 * min, 2, 20_000) // only 2 min
        assertEquals(0, SegmentDetector().detect(pts).count { it.type == SegmentType.VISIT })
    }

    @Test fun inaccurateFixesIgnored() {
        val pts = stay(home.first, home.second, 0, 30) + P(31 * min, 41.0, -75.0, 2000f)
        val segs = SegmentDetector().detect(pts)
        assertEquals(1, segs.size)
    }

    @Test fun googleFormatsParse() {
        assertEquals(47.6062 to -122.3321, GoogleImport.parseLatLng("47.6062°, -122.3321°"))
        assertEquals(47.6 to -122.3, GoogleImport.parseLatLng("geo:47.6,-122.3"))
        assertNull(GoogleImport.parseLatLng("nope"))
        assertEquals(1_700_000_000_000L, GoogleImport.parseTime("2023-11-14T22:13:20.000Z"))
        assertEquals(1_700_000_000_000L, GoogleImport.parseTime("2023-11-14T14:13:20.000-08:00"))
        assertEquals("IN_VEHICLE", GoogleImport.activityLabel("IN_PASSENGER_VEHICLE"))
        assertEquals("ON_BICYCLE", GoogleImport.activityLabel("cycling"))
        assertEquals("WALKING", GoogleImport.activityLabel("walking"))
    }
}
