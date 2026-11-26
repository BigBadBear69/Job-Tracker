package com.jobtracker.timeline.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import com.jobtracker.timeline.TimelineApp
import com.jobtracker.timeline.data.Place
import com.jobtracker.timeline.data.PlaceCategory
import com.jobtracker.timeline.data.Segment
import com.jobtracker.timeline.processing.Processor
import java.time.LocalDate
import java.util.Calendar

/** Edits the user can make, shared by several screens. */
object Actions {
    private val dao get() = TimelineApp.instance.dao

    suspend fun setVisitPlace(seg: Segment, place: Place) =
        dao.updateSegment(seg.copy(placeId = place.id, edited = true))

    suspend fun createPlace(name: String, category: PlaceCategory, lat: Double, lon: Double, address: String?): Place {
        val p = Place(name = name.trim(), lat = lat, lon = lon, category = category, address = address)
        val saved = p.copy(id = dao.insertPlace(p))
        Processor.attachVisitsToPlace(saved)
        return saved
    }

    suspend fun updateSegment(seg: Segment) = dao.updateSegment(seg.copy(edited = true))

    /** Deletes a visit or trip together with its recorded points so it doesn't come back. */
    suspend fun deleteSegment(seg: Segment) {
        dao.deletePointsBetween(seg.startTime, seg.endTime)
        dao.deleteSegment(seg)
    }

    suspend fun deleteRange(from: LocalDate, toInclusive: LocalDate) {
        val a = Fmt.startOf(from); val b = Fmt.startOf(toInclusive.plusDays(1)) - 1
        dao.deletePointsBetween(a, b)
        dao.deleteSegmentsBetween(a, b)
    }

    suspend fun deleteAll() {
        dao.deleteAllPoints(); dao.deleteAllSegments()
    }

    suspend fun deletePlace(p: Place) {
        dao.clearPlace(p.id); dao.deletePlace(p)
    }
}

/** Date picker followed by time picker. */
fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(context, { _, y, m, d ->
        cal.set(y, m, d)
        TimePickerDialog(context, { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            onPicked(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}
