package com.example.wfappops.home.model

import com.example.wfappops.orders.model.DispatchLineKey
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.TableWorker
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDayTest {
    @Test
    fun limaDayIncludesItsStartAndExcludesTheNextMidnight() {
        val day = OrderDay(LocalDate.of(2026, 11, 20))
        assertEquals(Instant.parse("2026-11-20T05:00:00Z"), day.start)
        assertEquals(Instant.parse("2026-11-21T05:00:00Z"), day.end)
        assertFalse(day.start.minusNanos(1) in day)
        assertTrue(day.start in day)
        assertTrue(day.end.minusNanos(1) in day)
        assertFalse(day.end in day)
    }

    @Test
    fun skipsOlderClosedOrdersButPreservesOpenOrdersAndRecentClosedOrders() {
        val worker = TableWorker("ana", "Ana")
        val older = Instant.parse("2026-11-18T18:00:00Z")
        val today = Instant.parse("2026-11-20T18:00:00Z")
        var service = TableService()
        for (table in 1..3) {
            service = service.open(table, worker)
            val id = requireNotNull(service.openVisit(table)).id
            service = service.add(id, OrderLine("Espresso", 1, 800), if (table == 3) today else older)
                .sendLocally(id, worker, if (table == 3) today else older)
            if (table != 2) service = service.close(id)
        }
        val orders = service.operationalOrders(OrderHistoryWindow(LocalDate.of(2026, 11, 20)))
        assertEquals(setOf(2, 3), orders.map { it.table }.toSet())
        assertEquals(3, service.visits.size) // La selección no elimina el historial guardado.
    }

    @Test
    fun twoDayHistoryUsesCalendarDaysInLimaAndHandlesMonthRollover() {
        val window = OrderHistoryWindow(LocalDate.of(2026, 12, 1))
        assertEquals(Instant.parse("2026-11-30T05:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-12-02T05:00:00Z"), window.end)
        assertFalse(window.start.minusNanos(1) in window)
        assertTrue(window.start in window)
        assertTrue(window.end.minusNanos(1) in window)
        assertFalse(window.end in window)
    }

    @Test
    fun projectionIncludesYesterdaysClosedOrdersWithoutDeletingHistory() {
        val worker = TableWorker("worker", "Colaborador")
        val opened = TableService().open(1, worker)
        val id = requireNotNull(opened.openVisit(1)).id
        val service = opened.add(id, OrderLine("Producto", 1, 800), Instant.parse("2026-11-19T18:00:00Z"))
            .sendLocally(id, worker, Instant.parse("2026-11-19T18:00:00Z")).close(id)
        assertEquals(listOf(1), service.operationalOrders(OrderHistoryWindow(LocalDate.of(2026, 11, 20))).map { it.table })
        assertTrue(service.operationalOrders(OrderHistoryWindow(LocalDate.of(2026, 11, 21))).isEmpty())
        assertEquals(1, service.visits.size)
    }

    @Test
    fun laterDispatchAndStatusChangeDoNotMoveAnOrderToTheFollowingDay() {
        val worker = TableWorker("ana", "Ana")
        val firstSent = Instant.parse("2026-11-19T23:00:00Z")
        val nextDay = Instant.parse("2026-11-20T15:00:00Z")
        val opened = TableService().open(1, worker, firstSent)
        val id = requireNotNull(opened.openVisit(1)).id
        val line = OrderLine("Espresso", 1, 800)
        val updated = opened.add(id, line, firstSent).sendLocally(id, worker, firstSent)
            .add(id, line).sendLocally(id, worker, nextDay)
            .advanceKitchenLine(DispatchLineKey(id, 0, 0), KitchenItemStatus.SENT, nextDay.plusSeconds(60))
        val order = updated.registeredOrders().single()
        assertTrue(order.startedAt in OrderDay(LocalDate.of(2026, 11, 19)))
        assertFalse(order.startedAt in OrderDay(LocalDate.of(2026, 11, 20)))
        assertEquals(nextDay.plusSeconds(60), order.lastActivityAt)
        val legacy = updated.copy(visits = updated.visits.map { it.copy(orderStartedAt = null) })
        assertEquals(order, legacy.registeredOrders().single())
    }
}
