package com.example.wfappops.home.model

import com.example.wfappops.orders.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class OrderProductsTest {
    @Test
    fun mixedOrderKeepsCanceledHistoryAndSummarizesDraftsSeparatelyFromKitchenFilters() {
        val worker = TableWorker("1", "Álvaro")
        val initial = TableService().open(1, worker)
        val visit = initial.visits.single()
        val lines = listOf(
            OrderLine("Café", 2, 1000, kitchenStatus = KitchenItemStatus.SENT),
            OrderLine("Brownie", 3, 900, kitchenStatus = KitchenItemStatus.IN_PREPARATION),
            OrderLine("Latte", 4, 1400, kitchenStatus = KitchenItemStatus.READY),
            OrderLine("Agua", 1, 500, kitchenStatus = KitchenItemStatus.DELIVERED),
            OrderLine("Anulado", 7, 100, kitchenStatus = KitchenItemStatus.CANCELED)
        )
        val service = TableService(listOf(visit.copy(orderNumber = 1,
            draft = listOf(OrderLine("Borrador", 20, 100)),
            dispatches = listOf(OrderDispatch(Instant.EPOCH, worker.name, lines, worker.id)))))
        val order = service.registeredOrders().single()
        assertEquals(17L, order.productsFor(HomeOrderFilter.ALL).sumOf { it.quantity })
        assertEquals(5L, order.productsFor(HomeOrderFilter.PREPARING).sumOf { it.quantity })
        assertEquals(4L, order.productsFor(HomeOrderFilter.READY).sumOf { it.quantity })
        assertEquals(setOf("Café", "Brownie"), order.productsFor(HomeOrderFilter.PREPARING).map { it.name }.toSet())
        assertEquals(KitchenItemStatus.CANCELED, order.products.first { it.name == "Anulado" }.status)
        assertTrue(order.products.none { it.name == "Borrador" })
        assertEquals(listOf(DraftProductSummary("Borrador", 20)), order.draftProducts)
        assertFalse(order.isFullyReady)
    }

    @Test
    fun fullReadinessRequiresEveryUnservedProductAndNoDraftRegardlessOfReadyFilter() {
        val worker = TableWorker("1", "Colaborador")
        val time = Instant.parse("2026-11-24T21:00:00Z")
        val line = OrderLine("Café", 1, 800)
        val visit = TableVisit("visit", 1, orderNumber = 1, orderStartedAt = time, openedBy = worker,
            dispatches = listOf(OrderDispatch(time, worker.name, listOf(line.asSent(), line.copy(product = "Agua").asSent()), worker.id)))
        val firstKey = DispatchLineKey(visit.id, 0, 0)
        val secondKey = DispatchLineKey(visit.id, 0, 1)
        val initial = TableService(listOf(visit))
        assertFalse(initial.registeredOrders().single().isFullyReady)
        val preparing = initial.advanceKitchenLine(firstKey, KitchenItemStatus.SENT, time.plusSeconds(1))
        val partlyReady = preparing.advanceKitchenLine(firstKey, KitchenItemStatus.IN_PREPARATION, time.plusSeconds(2))
        val partial = partlyReady.registeredOrders().single()
        assertEquals(RegisteredOrderStatus.READY, partial.status)
        assertTrue(partial.productsFor(HomeOrderFilter.READY).isNotEmpty())
        assertFalse(partial.isFullyReady)
        val allReady = partlyReady.advanceKitchenLine(secondKey, KitchenItemStatus.SENT, time.plusSeconds(3))
            .advanceKitchenLine(secondKey, KitchenItemStatus.IN_PREPARATION, time.plusSeconds(4))
        assertTrue(allReady.registeredOrders().single().isFullyReady)
        assertEquals(time.plusSeconds(4), allReady.registeredOrders().single().lastActivityAt)
        val withDraft = allReady.add(visit.id, line, time.plusSeconds(5), worker)
        assertTrue(withDraft.registeredOrders().single().hasDraftProducts)
        assertFalse(withDraft.registeredOrders().single().isFullyReady)
        val firstDelivered = allReady.deliverLine(firstKey, time.plusSeconds(6))
        assertTrue(firstDelivered.registeredOrders().single().isFullyReady)
        assertFalse(firstDelivered.deliverLine(secondKey, time.plusSeconds(7)).registeredOrders().single().isFullyReady)
    }

    @Test
    fun canceledProductsDoNotBlockFullReadinessButClosedAttentionIsNotReady() {
        val time = Instant.EPOCH
        val lines = listOf(OrderLine("Listo", 1, 800, kitchenStatus = KitchenItemStatus.READY),
            OrderLine("Anulado", 1, 800, kitchenStatus = KitchenItemStatus.CANCELED))
        val service = TableService(listOf(TableVisit("visit", 1, orderNumber = 1, orderStartedAt = time,
            dispatches = listOf(OrderDispatch(time, "Colaborador", lines)))))
        assertTrue(service.registeredOrders().single().isFullyReady)
        assertFalse(service.close("visit").registeredOrders().single().isFullyReady)
    }

    @Test
    fun draftOnlyHasNoKitchenDispatchesButCanceledDispatchesStillCountAsSentHistory() {
        val worker = TableWorker("1", "Colaborador")
        val time = Instant.parse("2026-11-24T21:00:00Z")
        val opened = TableService().open(1, worker, time)
        val id = opened.visits.single().id
        val line = OrderLine("Café", 1, 800)
        val draft = opened.add(id, line, time, worker)
        assertFalse(draft.registeredOrders().single().hasKitchenDispatches)
        val canceled = draft.sendLocally(id, worker, time.plusSeconds(1))
            .cancelLocalLines(id, 0, setOf(0), worker, time.plusSeconds(2))
            .add(id, line, time.plusSeconds(3), worker)
        val order = canceled.registeredOrders().single()
        assertTrue(order.hasKitchenDispatches)
        assertTrue(order.hasDraftProducts)
        assertEquals(KitchenItemStatus.CANCELED, order.products.single().status)
        assertTrue(order.productsFor(HomeOrderFilter.PREPARING).isEmpty())
        assertFalse(order.isFullyReady)
    }

    @Test
    fun cashierClosureKeepsItsTimeAndDisappearsFromHomeWhileRemainingInHistory() {
        val worker = TableWorker("1", "Colaborador")
        val time = Instant.parse("2026-11-24T21:00:00Z")
        val visit = TableVisit("visit", 1, orderNumber = 1, orderStartedAt = time, openedBy = worker,
            dispatches = listOf(OrderDispatch(time, worker.name,
                listOf(OrderLine("Café", 1, 800, kitchenStatus = KitchenItemStatus.DELIVERED)), worker.id)))
        val closed = TableService(listOf(visit)).sendToCashier(visit.id, worker, time.plusSeconds(60))
        val order = closed.registeredOrders().single()
        assertEquals(RegisteredOrderStatus.CLOSED, order.status)
        assertEquals(time.plusSeconds(60), order.cashierHandoffAt)
        assertEquals(order.cashierHandoffAt, order.lastActivityAt)
        val state = com.example.wfappops.home.viewmodel.OrderHistoryUiState(
            com.example.wfappops.home.viewmodel.OrderHistoryScope("business", worker.id),
            Result.success(com.example.wfappops.home.viewmodel.OrderOverview(listOf(order), closed.serviceTables())),
            java.time.LocalDate.of(2026, 11, 24))
        HomeOrderFilter.entries.forEach { assertTrue(state.homeOrders(it)!!.getOrThrow().isEmpty()) }
        assertEquals(listOf(order), state.orders(true)!!.getOrThrow())
    }

    @Test
    fun removingOnlyDraftHidesEmptyOrderButRetainsCanceledNumberForAudit() {
        val worker = TableWorker("1", "Colaborador")
        val time = Instant.parse("2026-11-24T21:00:00Z")
        val initial = TableService().open(1, worker, time)
        val id = initial.visits.single().id
        val line = OrderLine("Borrador", 3, 800)
        val saved = initial.add(id, line, time, worker)
        assertTrue(saved.registeredOrders().single().hasDisplayableProducts)
        assertEquals(3L, saved.registeredOrders().single().draftProducts.single().quantity)
        val removed = saved.adjustDraft(id, line, -3)
        assertFalse(removed.registeredOrders().single().hasDisplayableProducts)
        assertEquals(1, removed.registeredOrders().single().number)
        assertTrue(removed.visits.single().closed)
        assertFalse(removed.visits.single().hasRecordedProducts)
    }
}
