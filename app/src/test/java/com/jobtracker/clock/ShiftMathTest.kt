package com.jobtracker.clock

import com.jobtracker.clock.data.BreakEntry
import com.jobtracker.clock.data.Shift
import com.jobtracker.clock.data.ShiftWithBreaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftMathTest {
    private val min = 60_000L
    private val h = 60 * min

    @Test fun completedShiftSubtractsBreaks() {
        val s = ShiftWithBreaks(
            Shift(1, startTime = 0, endTime = 8 * h),
            listOf(
                BreakEntry(1, 1, startTime = 4 * h, endTime = 4 * h + 30 * min, note = "Lunch"),
                BreakEntry(2, 1, startTime = 2 * h, endTime = 2 * h + 15 * min),
            ),
        )
        assertEquals(8 * h, s.totalMillis(now = 99 * h))
        assertEquals(45 * min, s.breakMillis(now = 99 * h))
        assertEquals(7 * h + 15 * min, s.workedMillis(now = 99 * h))
    }

    @Test fun activeBreakCountsUntilNow() {
        val s = ShiftWithBreaks(
            Shift(1, startTime = 0),
            listOf(BreakEntry(1, 1, startTime = 3 * h)),
        )
        val now = 3 * h + 20 * min
        assertEquals(20 * min, s.breakMillis(now))
        assertEquals(3 * h, s.workedMillis(now))
        assertEquals(1L, s.activeBreak?.id)
    }

    @Test fun formatting() {
        assertEquals("1:05:09", Format.clock(h + 5 * min + 9_000))
        assertEquals("45m", Format.hm(45 * min))
        assertEquals("7h 15m", Format.hm(7 * h + 15 * min))
    }

    @Test fun csvHasHeaderAndQuotedNotes() {
        val s = ShiftWithBreaks(
            Shift(1, startTime = 0, endTime = 8 * h),
            listOf(BreakEntry(1, 1, startTime = h, endTime = 2 * h, note = "Lunch, \"big\"")),
        )
        val lines = CsvExport.build(listOf(s)).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("Date,Clock In,Clock Out,Worked (h)"))
        assertTrue(lines[1].contains(",7.00,1.00,8.00,"))
        assertTrue(lines[1].contains("\"Lunch, \"\"big\"\"")) 
    }
}
