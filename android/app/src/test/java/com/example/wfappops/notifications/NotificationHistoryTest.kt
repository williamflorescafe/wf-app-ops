package com.example.wfappops.notifications

import com.example.wfappops.orders.model.DispatchLineKey
import com.example.wfappops.orders.model.ReadyProductNotice
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NotificationHistoryTest {
    private val time = Instant.parse("2026-11-26T17:00:00Z")
    private fun notice(unit: String = "one") = ReadyProductNotice(DispatchLineKey("visit", 0, 0), 2, "Café", 1,
        readyAt = null, tableLabel = "2", orderNumber = 12, locationId = "1", stableKey = "unit/$unit",
        groupKey = "order/round/line", locationName = "San Borja")

    @Test fun firstObservationIsStoredOnceWithoutInventingServerTransitionTime() {
        val update = NotificationHistory().reconcile(listOf(notice()), time)
        assertEquals(time, update.arrivals.single().receivedAt)
        assertEquals(12, update.arrivals.single().orderNumber)
        val repeated = update.history.reconcile(listOf(notice()), time.plusSeconds(20))
        assertEquals(update.history, repeated.history)
        assertTrue(repeated.arrivals.isEmpty())
        assertTrue(repeated.reminders.isEmpty())
    }

    @Test fun reminderStartsAtFiveMinutesAndDoesNotFloodTheHistory() {
        val first = NotificationHistory().reconcile(listOf(notice()), time).history
        assertTrue(first.reconcile(listOf(notice()), time.plusSeconds(299)).reminders.isEmpty())
        val reminder = first.reconcile(listOf(notice()), time.plusSeconds(300))
        assertEquals(LocalNotificationKind.SERVE_REMINDER, reminder.reminders.single().kind)
        val repeated = reminder.history.reconcile(listOf(notice()), time.plusSeconds(301))
        assertTrue(repeated.reminders.isEmpty())
        val next = repeated.history.reconcile(listOf(notice()), time.plusSeconds(600))
        assertEquals(2, next.history.entries.size)
        assertEquals(time.plusSeconds(600), next.reminders.single().receivedAt)
    }

    @Test fun servingStopsRemindersButPreservesBothHistoricalEntries() {
        val ready = NotificationHistory().reconcile(listOf(notice()), time).history
        val reminded = ready.reconcile(listOf(notice()), time.plusSeconds(300)).history
        val served = reminded.reconcile(emptyList(), time.plusSeconds(900))
        assertEquals(reminded, served.history)
        assertTrue(served.reminders.isEmpty())
        assertEquals(2, served.history.entries.size)
    }

    @Test fun restartPreservesDedupeAndReminderDeadline() {
        val first = NotificationHistory().reconcile(listOf(notice()), time).history
        val restored = NotificationHistory.decode(first.encode())
        assertEquals(first, restored)
        assertTrue(restored.reconcile(listOf(notice()), time.plusSeconds(299)).arrivals.isEmpty())
        val reminder = restored.reconcile(listOf(notice()), time.plusSeconds(300))
        assertEquals(reminder.history, NotificationHistory.decode(reminder.history.encode()))
    }

    @Test fun unitRegroupingAndTemporaryMissingDetailDoNotDuplicateReadyNotice() {
        val initial = NotificationHistory().reconcile(listOf(notice()), time).history
        val missing = initial.reconcile(emptyList(), time.plusSeconds(1)).history
        val reordered = notice().copy(key = DispatchLineKey("visit", 0, 7))
        val reread = missing.reconcile(listOf(reordered), time.plusSeconds(2))
        assertEquals(initial, reread.history)
        assertTrue(reread.arrivals.isEmpty())
    }

    @Test fun simultaneousUnitsAreGroupedAndOnlyNewUnitsGenerateAnotherArrival() {
        val first = NotificationHistory().reconcile(listOf(notice("one"), notice("two")), time)
        assertEquals(2, first.arrivals.single().quantity)
        val next = first.history.reconcile(listOf(notice("two"), notice("three")), time.plusSeconds(120))
        assertEquals(1, next.arrivals.single().quantity)
        val reminder = next.history.reconcile(listOf(notice("three")), time.plusSeconds(420))
        assertEquals(1, reminder.reminders.single().quantity)
        assertEquals(3, reminder.history.entries.size)
    }

    @Test fun clockGoingBackDoesNotMakeNegativeAgesOrPrematureReminders() {
        val saved = NotificationHistory().reconcile(listOf(notice()), time).history
        assertTrue(saved.reconcile(listOf(notice()), time.minusSeconds(3600)).reminders.isEmpty())
        assertEquals(0L, notificationElapsedSeconds(time, time.minusSeconds(1)))
    }
}
