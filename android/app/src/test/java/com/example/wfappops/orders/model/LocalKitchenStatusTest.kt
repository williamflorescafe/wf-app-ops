package com.example.wfappops.orders.model

import com.example.wfappops.notifications.NotificationHistory
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LocalKitchenStatusTest {
    private val time = Instant.parse("2026-11-26T20:00:00Z")
    private val worker = TableWorker("worker", "Colaborador")
    private val key = DispatchLineKey("visit", 0, 0)
    private val original = OrderLine("Café", 2, 800, kitchenStatus = KitchenItemStatus.SENT,
        remoteUnitIds = listOf("unit-a", "unit-b"), remoteLineNumber = 1)
    private fun source() = TableService(listOf(TableVisit("visit", 2, apiBacked = true, orderNumber = 12,
        remoteOrderId = "order", remoteVersion = 1, remoteStatus = "open", remoteDetailLoaded = true,
        dispatches = listOf(OrderDispatch(time, worker.name, listOf(original), worker.id, remoteId = "round")))))
    private fun TableService.current() = withLocalKitchenStatuses().visits.single().dispatches.single().lines.first()
    private fun TableService.advance(at: Instant) = advanceLocalKitchenStatus(key, current(), 1, at)

    @Test fun progressesToServedWithoutChangingAnyOriginalServerLine() {
        var stored = source()
        listOf(KitchenItemStatus.IN_PREPARATION, KitchenItemStatus.READY, KitchenItemStatus.DELIVERED).forEachIndexed { index, status ->
            stored = stored.advance(time.plusSeconds(index + 1L))
            assertEquals(status, stored.current().dispatchStatus)
            assertEquals(original, stored.visits.single().dispatches.single().lines.single())
            assertEquals(1, stored.visits.single().remoteVersion)
            assertEquals(12, stored.visits.single().orderNumber)
            assertEquals(2, stored.current().quantity)
        }
    }

    @Test fun readyAndServedDriveTheNotificationCacheAndKeepItsHistory() {
        val ready = source().advance(time.plusSeconds(1)).advance(time.plusSeconds(2))
        val notices = ready.withLocalKitchenStatuses().readyNoticesFor(worker.id)
        assertEquals(2, notices.size)
        assertTrue(notices.all { it.readyAt == time.plusSeconds(2) })
        val history = NotificationHistory().reconcile(notices, time.plusSeconds(2)).history
        assertEquals(2, history.entries.single().quantity)
        val served = ready.advance(time.plusSeconds(3)).withLocalKitchenStatuses().readyNoticesFor(worker.id)
        assertTrue(served.isEmpty())
        assertEquals(history, history.reconcile(served, time.plusSeconds(700)).history)
    }

    @Test fun sameVersionReloadAndSerializationPreserveLocalProgress() {
        val stored = source().advance(time.plusSeconds(1))
        val visit = stored.visits.single()
        val restored = stored.copy(visits = listOf(visit.copy(localKitchenStatuses = visit.localKitchenStatuses.mapValues {
            LocalKitchenStatus.decode(it.value.encode())
        })))
        assertEquals(stored.current(), restored.current())
        assertEquals(KitchenItemStatus.IN_PREPARATION, restored.current().dispatchStatus)
    }

    @Test fun consecutiveClicksWorkAfterCacheRoundsTimestampsToMilliseconds() {
        var displayed = source().advance(time.plusNanos(123456789))
        for (status in listOf(KitchenItemStatus.READY, KitchenItemStatus.DELIVERED)) {
            val onScreen = displayed.current()
            val visit = displayed.visits.single()
            val disk = displayed.copy(visits = listOf(visit.copy(localKitchenStatuses = visit.localKitchenStatuses.mapValues {
                LocalKitchenStatus.decode(it.value.encode())
            })))
            displayed = disk.advanceLocalKitchenStatus(key, onScreen, 1, time.plusSeconds(2).plusNanos(987654321))
            assertEquals(status, displayed.current().dispatchStatus)
        }
    }

    @Test fun newerServerVersionOrChangedServerStatusTakesPriority() {
        val stored = source().advance(time.plusSeconds(1))
        val visit = stored.visits.single()
        assertEquals(KitchenItemStatus.SENT, stored.copy(visits = listOf(visit.copy(remoteVersion = 2))).current().dispatchStatus)
        val remote = visit.dispatches.single().copy(lines = listOf(original.copy(kitchenStatus = KitchenItemStatus.READY)))
        assertEquals(KitchenItemStatus.READY, stored.copy(visits = listOf(visit.copy(dispatches = listOf(remote)))).current().dispatchStatus)
    }

    @Test fun stableUnitIdentityKeepsTheCorrectProductAfterReordering() {
        val stored = source().advance(time.plusSeconds(1))
        val visit = stored.visits.single()
        val other = original.copy(product = "Agua", remoteUnitIds = listOf("other"), quantity = 1)
        val reordered = visit.dispatches.single().copy(lines = listOf(other, original.copy(remoteUnitIds = original.remoteUnitIds.reversed())))
        val projected = stored.copy(visits = listOf(visit.copy(dispatches = listOf(reordered)))).withLocalKitchenStatuses()
        val lines = projected.visits.single().dispatches.single().lines
        assertEquals(KitchenItemStatus.SENT, lines.first().dispatchStatus)
        assertEquals(KitchenItemStatus.IN_PREPARATION, lines.last().dispatchStatus)
    }

    @Test fun staleClickAndClosedOrderCannotAdvance() {
        val stored = source().advance(time.plusSeconds(1))
        assertThrows(IllegalStateException::class.java) {
            stored.advanceLocalKitchenStatus(key, original, 1, time.plusSeconds(2))
        }
        assertThrows(IllegalStateException::class.java) {
            stored.advanceLocalKitchenStatus(key, stored.current(), 2, time.plusSeconds(2))
        }
        val closed = stored.copy(visits = stored.visits.map { it.copy(closed = true) })
        assertThrows(IllegalStateException::class.java) {
            closed.advanceLocalKitchenStatus(key, original, 1, time.plusSeconds(2))
        }
    }
}
