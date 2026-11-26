package com.jobtracker.clock.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A single work shift. [endTime] is null while the user is still clocked in. */
@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
)

/** A break inside a shift. [endTime] is null while the break is in progress. */
@Entity(
    tableName = "breaks",
    foreignKeys = [
        ForeignKey(
            entity = Shift::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("shiftId")],
)
data class BreakEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val note: String? = null,
)

data class ShiftWithBreaks(
    @Embedded val shift: Shift,
    @Relation(parentColumn = "id", entityColumn = "shiftId")
    val breaks: List<BreakEntry>,
) {
    val sortedBreaks: List<BreakEntry> get() = breaks.sortedBy { it.startTime }
    val activeBreak: BreakEntry? get() = breaks.firstOrNull { it.endTime == null }

    fun totalMillis(now: Long): Long = ((shift.endTime ?: now) - shift.startTime).coerceAtLeast(0)

    fun breakMillis(now: Long): Long = breaks.sumOf { b ->
        ((b.endTime ?: shift.endTime ?: now) - b.startTime).coerceAtLeast(0)
    }

    fun workedMillis(now: Long): Long = (totalMillis(now) - breakMillis(now)).coerceAtLeast(0)
}
