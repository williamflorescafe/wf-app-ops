package com.example.wfappops.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.YearMonth

class ScheduleMonthTest {
    private val month = YearMonth.of(2026, 12)

    @Test
    fun collectsAllPagesAndDeduplicatesRowsById() {
        val first = shift("1", 3)
        val last = shift("2", 20)
        val cursors = mutableListOf<String?>()

        val result = collectScheduleMonth(month) { cursor ->
            cursors += cursor
            if (cursor == null) SchedulePage(listOf(first), "next") else SchedulePage(listOf(first, last), null)
        }

        assertEquals(listOf(null, "next"), cursors)
        assertEquals(listOf(first, last), result)
    }

    @Test
    fun rejectsAnIncompleteMonthInsteadOfReturningEarlierPages() {
        assertThrows(ScheduleProtocolException::class.java) {
            collectScheduleMonth(month) { SchedulePage(listOf(shift("1", 3)), "repeated") }
        }
    }

    @Test
    fun anEmptyResponseIsAValidCompletedMonth() {
        assertEquals(emptyList<ScheduledShift>(), collectScheduleMonth(month) { SchedulePage(emptyList(), null) })
    }

    @Test
    fun retentionIncludesTheNextMonthAcrossNewYear() {
        val retained = scheduleRetention(month)
        assertEquals(YearMonth.of(2026, 10), retained.start)
        assertEquals(YearMonth.of(2027, 1), retained.endInclusive)
    }

    private fun shift(id: String, day: Int) = ScheduledShift(
        id, month.atDay(day), limaZone, "work", "Apertura", null, null, 30, 450
    )
}
