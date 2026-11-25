package com.example.wfappops.home.viewmodel

import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.DispatchLineKey
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.ReadyProductSummary
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.RegisteredOrderStatus
import com.example.wfappops.home.model.HomeOrderFilter
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderHistoryUiStateTest {
    @Test
    fun statusChangesMoveTheirOrderToTheTopWithoutPinningOlderReadyProducts() {
        val worker = TableWorker("1", "Colaborador")
        val time = Instant.parse("2026-11-24T21:00:00Z")
        var service = TableService().open(1, worker, time).open(2, worker, time)
        val first = service.openVisit(1)!!.id
        val second = service.openVisit(2)!!.id
        val line = OrderLine("Café", 1, 800)
        service = service.add(first, line, time, worker)
            .add(first, line.copy(product = "Agua"), time, worker).sendLocally(first, worker, time)
            .add(second, line, time.plusSeconds(1), worker).sendLocally(second, worker, time.plusSeconds(1))
        fun state() = OrderHistoryUiState(OrderHistoryScope("business", worker.id),
            Result.success(OrderOverview(service.registeredOrders(), service.serviceTables())), java.time.LocalDate.of(2026, 11, 24))
        fun tables() = state().myOpenOrders!!.getOrThrow().map { it.table }
        assertEquals(listOf(2, 1), tables())
        service = service.advanceKitchenLine(DispatchLineKey(first, 0, 0), KitchenItemStatus.SENT, time.plusSeconds(2))
        assertEquals(listOf(1, 2), tables())
        service = service.advanceKitchenLine(DispatchLineKey(first, 0, 0), KitchenItemStatus.IN_PREPARATION, time.plusSeconds(3))
        assertEquals(listOf(1, 2), tables())
        org.junit.Assert.assertFalse(state().myOpenOrders!!.getOrThrow().first().isFullyReady)
        service = service.advanceKitchenLine(DispatchLineKey(second, 0, 0), KitchenItemStatus.SENT, time.plusSeconds(4))
        assertEquals(listOf(2, 1), tables())
        service = service.advanceKitchenLine(DispatchLineKey(first, 0, 1), KitchenItemStatus.SENT, time.plusSeconds(5))
            .advanceKitchenLine(DispatchLineKey(first, 0, 1), KitchenItemStatus.IN_PREPARATION, time.plusSeconds(6))
        assertEquals(listOf(1, 2), tables())
        assertTrue(state().myOpenOrders!!.getOrThrow().first().isFullyReady)
        service = service.close(first)
        assertEquals(listOf(2), tables())
    }

    @Test
    fun myOrdersFollowLatestActivityAcrossDatesWithoutPinningOlderReadyOrders() {
        val ana = TableWorker("1", "Ana")
        val juan = TableWorker("2", "Juan")
        val opened = TableService().open(1, ana).open(2, ana).open(3, juan)
        val first = requireNotNull(opened.openVisit(1)).id
        val second = requireNotNull(opened.openVisit(2)).id
        val third = requireNotNull(opened.openVisit(3)).id
        val item = OrderLine("Espresso", 2, 800)
        val sent = opened.add(first, item, Instant.EPOCH).add(first, item.copy(quantity = 1, note = "Sin azúcar"))
            .sendLocally(first, ana, Instant.EPOCH)
            .add(second, item, Instant.EPOCH.plusSeconds(172_800)).sendLocally(second, ana, Instant.EPOCH.plusSeconds(172_800))
            .add(third, item.copy(kitchenStatus = KitchenItemStatus.READY), Instant.EPOCH.plusSeconds(172_801))
            .sendLocally(third, juan, Instant.EPOCH.plusSeconds(172_801))
        val ready = (0..1).fold(sent) { service, index ->
            val key = DispatchLineKey(first, 0, index)
            service.advanceKitchenLine(key, KitchenItemStatus.SENT, Instant.EPOCH.plusSeconds(1))
                .advanceKitchenLine(key, KitchenItemStatus.IN_PREPARATION, Instant.EPOCH.plusSeconds(2))
        }
        val state = OrderHistoryUiState(
            OrderHistoryScope("business", ana.id),
            Result.success(OrderOverview(ready.registeredOrders(), ready.serviceTables())),
            day = LocalDate.of(1970, 1, 2)
        )
        val myOrders = requireNotNull(state.myOpenOrders).getOrThrow()
        assertEquals(listOf(2, 1), myOrders.map { it.table })
        assertEquals(3L, myOrders.last().readyQuantity)
        assertEquals(listOf(ReadyProductSummary("Espresso", 3)), myOrders.last().readyProducts)
        assertEquals(listOf(3, 2), state.orders(onlyMine = false)?.getOrThrow()?.map { it.table })

        val delivered = ready.deliverDispatch(first, 0, Instant.EPOCH.plusSeconds(3))
        val updated = state.copy(result = Result.success(OrderOverview(delivered.registeredOrders(), delivered.serviceTables())))
        val afterServing = requireNotNull(updated.myOpenOrders).getOrThrow()
        assertEquals(listOf(2, 1), afterServing.map { it.table })
        assertEquals(emptyList<ReadyProductSummary>(), afterServing.last().readyProducts)
    }

    @Test
    fun homeShowsMyOpenOrdersWhileHistoryCanFilterAndTablesShowEveryone() {
        val ana = TableWorker("1", "Ana")
        val juan = TableWorker("2", "Juan")
        val opened = TableService().open(1, ana).open(2, juan).open(3, ana).open(4, ana)
        val first = requireNotNull(opened.openVisit(1)).id
        val second = requireNotNull(opened.openVisit(2)).id
        val third = requireNotNull(opened.openVisit(3)).id
        val fourth = requireNotNull(opened.openVisit(4)).id
        val item = OrderLine("Espresso", 1, 800)
        val start = Instant.EPOCH
        val service = opened
            .add(first, item, start).sendLocally(first, ana, start)
            .transfer(first, juan, ana, start.plusSeconds(1))
            .add(second, item, start.plusSeconds(2)).sendLocally(second, juan, start.plusSeconds(2))
            .add(third, item, start.plusSeconds(3)).sendLocally(third, ana, start.plusSeconds(3)).close(third)
            .add(fourth, item, start.plusSeconds(4))
        val state = OrderHistoryUiState(
            OrderHistoryScope("business", ana.id),
            Result.success(OrderOverview(service.registeredOrders(), service.serviceTables())),
            day = LocalDate.of(1969, 12, 31)
        )

        assertEquals(listOf(4, 1), state.myOpenOrders?.getOrThrow()?.map { it.table })
        assertEquals(listOf(4, 3, 1), state.orders(onlyMine = true)?.getOrThrow()?.map { it.table })
        assertEquals(listOf(4, 3, 2, 1), state.orders(onlyMine = false)?.getOrThrow()?.map { it.table })
        val tables = requireNotNull(state.tables).getOrThrow()
        assertEquals(listOf(1, 2, 3, 4), tables.map { it.number })
        assertEquals(juan, tables.first().responsible)
        assertNull(tables.first { it.number == 3 }.visitId)
        assertEquals(fourth, tables.first { it.number == 4 }.visitId)
    }

    @Test
    fun twoDayHistoryIncludesClosedOrdersAndOwnerFilterWhileHomeOnlyShowsMyOpenOrders() {
        val day = LocalDate.of(2026, 11, 20)
        fun order(number: Int, start: String, status: RegisteredOrderStatus, employee: String = "ana") = RegisteredOrder(
            visitId = "visit-$number", table = number, number = number,
            startedAt = Instant.parse(start), lastActivityAt = Instant.parse("2026-11-20T22:00:00Z"),
            status = status, readyQuantity = if (status == RegisteredOrderStatus.READY) 1 else 0,
            employeeIds = setOf(employee),
            products = listOf(com.example.wfappops.orders.model.OrderProductSummary("Café", 1, when (status) {
                RegisteredOrderStatus.SENT -> KitchenItemStatus.SENT
                RegisteredOrderStatus.PREPARING -> KitchenItemStatus.IN_PREPARATION
                RegisteredOrderStatus.READY -> KitchenItemStatus.READY
                else -> KitchenItemStatus.DELIVERED
            }))
        )
        val orders = listOf(
            order(1, "2026-11-20T04:59:59Z", RegisteredOrderStatus.SENT), // Ayer en Lima, sigue abierta.
            order(2, "2026-11-20T05:00:00Z", RegisteredOrderStatus.SENT),
            order(3, "2026-11-20T06:00:00Z", RegisteredOrderStatus.PREPARING, "juan"),
            order(4, "2026-11-20T07:00:00Z", RegisteredOrderStatus.CLOSED),
            order(5, "2026-11-21T05:00:00Z", RegisteredOrderStatus.CLOSED), // Mañana, se excluye.
            order(6, "2026-11-20T08:00:00Z", RegisteredOrderStatus.PREPARING),
            order(7, "2026-11-20T09:00:00Z", RegisteredOrderStatus.READY),
            order(8, "2026-11-20T10:00:00Z", RegisteredOrderStatus.DELIVERED),
            order(9, "2026-11-19T04:59:59Z", RegisteredOrderStatus.CLOSED) // Anteayer en Lima, se excluye.
        )
        val state = OrderHistoryUiState(OrderHistoryScope("business", "ana"), Result.success(OrderOverview(orders, emptyList())), day)

        assertEquals(listOf(8, 7, 6, 4, 3, 2, 1), state.orders(onlyMine = false)?.getOrThrow()?.map { it.table })
        assertEquals(listOf(8, 7, 6, 4, 2, 1), state.orders(onlyMine = true)?.getOrThrow()?.map { it.table })
        assertEquals(listOf(6, 2, 1), state.homeOrders(HomeOrderFilter.PREPARING)?.getOrThrow()?.map { it.table })
        assertEquals(listOf(7), state.homeOrders(HomeOrderFilter.READY)?.getOrThrow()?.map { it.table })
        val all = requireNotNull(state.homeOrders(HomeOrderFilter.ALL)).getOrThrow()
        assertEquals(setOf(1, 2, 6, 7, 8), all.map { it.table }.toSet())
        assertEquals(8, all.first().table)
        assertSame(all, state.homeOrders(HomeOrderFilter.ALL)?.getOrThrow())
        assertTrue(all.none { it.status == RegisteredOrderStatus.CLOSED })
    }

    @Test
    fun discardedEmptyDraftKeepsAuditRecordButDoesNotAppearInHomeOrCommands() {
        val time = Instant.parse("2026-11-24T21:30:00Z")
        val worker = TableWorker("1", "Colaborador")
        val opened = TableService().open(3, worker, time)
        val id = opened.visits.single().id
        val line = OrderLine("Espresso", 1, 800)
        val discarded = opened.add(id, line, time, worker).adjustDraft(id, line, -1)
        val state = OrderHistoryUiState(OrderHistoryScope("business", worker.id),
            Result.success(OrderOverview(discarded.registeredOrders(), discarded.serviceTables())),
            day = LocalDate.of(2026, 11, 24))
        assertTrue(state.myOpenOrders!!.getOrThrow().isEmpty())
        assertTrue(state.orders(onlyMine = true)!!.getOrThrow().isEmpty())
        assertTrue(state.orders(onlyMine = false)!!.getOrThrow().isEmpty())
        assertEquals(RegisteredOrderStatus.CANCELED, discarded.registeredOrders().single().status)
    }

    @Test
    fun daySourceUsesLimaRegardlessOfTheDeviceClockZone() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-11-20T04:59:59Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 11, 19), orderDays(clock).first())
    }
}
