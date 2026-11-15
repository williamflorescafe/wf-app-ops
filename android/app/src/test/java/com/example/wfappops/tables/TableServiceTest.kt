package com.example.wfappops.tables

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableServiceTest {
    private val cappuccino = OrderLine("Cappuccino", 1, 1400, listOf("Alto", "Entera"))
    private val brownie = OrderLine("Brownie", 2, 900)
    private val pablito = TableWorker("1", "Pablito")
    private val juanito = TableWorker("2", "Juanito")

    @Test
    fun cancelingOneDispatchKeepsItsHistoryAndExcludesItFromTheTableTotal() {
        val initial = TableService().open(1, pablito)
        val id = requireNotNull(initial.openVisit(1)).id
        val sent = initial.add(id, cappuccino).sendLocally(id, pablito, Instant.EPOCH)
            .add(id, brownie).sendLocally(id, juanito, Instant.EPOCH.plusSeconds(10))
        val time = Instant.EPOCH.plusSeconds(20)
        val canceled = sent.cancelLocalDispatch(id, 0, juanito, time)
        val visit = requireNotNull(canceled.openVisit(1))
        assertEquals(2, visit.dispatches.size)
        assertEquals(listOf(cappuccino), visit.dispatches.first().lines)
        assertEquals(juanito, visit.dispatches.first().canceledBy)
        assertEquals(time, visit.dispatches.first().canceledAt)
        assertEquals(brownie.totalInCents, visit.totalInCents)
        assertEquals(canceled, canceled.cancelLocalDispatch(id, 0, pablito, time.plusSeconds(1)))
    }

    @Test
    fun firstDispatchAssignsIdentityAndLaterDispatchesKeepIt() {
        val started = TableService().open(4, pablito).open(5, juanito)
        val first = requireNotNull(started.openVisit(4))
        val second = requireNotNull(started.openVisit(5))
        assertNull(first.orderNumber)
        assertNull(first.orderStartedAt)
        val firstTime = Instant.parse("2026-11-11T15:30:00Z")
        val secondTime = firstTime.plusSeconds(60)
        val sent = started.add(second.id, brownie).sendLocally(second.id, juanito, firstTime)
            .add(first.id, cappuccino).sendLocally(first.id, pablito, firstTime)
            .add(first.id, brownie).sendLocally(first.id, juanito, secondTime)

        assertEquals(2, sent.openVisit(4)?.orderNumber)
        assertEquals(1, sent.openVisit(5)?.orderNumber)
        assertEquals(firstTime, sent.openVisit(4)?.orderStartedAt)
        assertEquals(listOf(firstTime, secondTime), sent.openVisit(4)?.dispatches?.map { it.sentAt })
    }

    @Test
    fun notesKeepOtherwiseIdenticalProductsSeparateAndSurviveSending() {
        val opened = TableService().open(1, pablito)
        val id = requireNotNull(opened.openVisit(1)).id
        val annotated = cappuccino.copy(note = "Sin espuma")
        val sent = opened.add(id, cappuccino).editDraftNote(id, cappuccino, annotated.note)
            .add(id, cappuccino).sendLocally(id, pablito, Instant.EPOCH)
        assertEquals(listOf(annotated, cappuccino), sent.openVisit(1)?.dispatches?.single()?.lines)
    }

    @Test
    fun eachTableKeepsItsOwnDraftAndAccumulatesOnlyRegisteredTickets() {
        var service = TableService().open(1, pablito).open(2, juanito)
        val first = requireNotNull(service.openVisit(1)).id
        val second = requireNotNull(service.openVisit(2)).id
        service = service.add(first, cappuccino).add(second, brownie)
        assertEquals(0L, service.openVisit(1)?.totalInCents)

        val firstTime = Instant.parse("2026-11-11T15:30:00Z")
        val secondTime = Instant.parse("2026-11-11T15:50:00Z")
        service = service.sendLocally(first, pablito, firstTime).sendLocally(first, pablito, firstTime)
            .add(first, brownie).sendLocally(first, juanito, secondTime)

        val account = requireNotNull(service.openVisit(1))
        assertEquals(3200L, account.totalInCents)
        assertEquals(emptyList<OrderLine>(), account.draft)
        assertEquals(1, account.orderNumber)
        assertEquals(listOf("Pablito", "Juanito"), account.dispatches.map { it.employeeName })
        assertEquals(listOf("1", "2"), account.dispatches.map { it.employeeId })
        assertEquals(listOf(firstTime, secondTime), account.dispatches.map { it.sentAt })
        assertEquals(listOf(brownie), service.openVisit(2)?.draft)
        assertEquals(0L, service.openVisit(2)?.totalInCents)
    }

    @Test
    fun reopeningATableStartsANewAccountAndRejectsOldVisitEdits() {
        var service = TableService().open(1, pablito)
        val first = requireNotNull(service.openVisit(1)).id
        service = service.add(first, cappuccino)
        assertEquals(service, service.close(first))
        service = service.sendLocally(first, pablito, Instant.EPOCH).close(first)
        assertNull(service.openVisit(1))

        service = service.open(1, juanito).add(first, brownie)
        assertEquals(emptyList<OrderLine>(), service.openVisit(1)?.draft)
        assertEquals(0L, service.openVisit(1)?.totalInCents)
        assertEquals(listOf(cappuccino), service.visits.first().dispatches.single().lines)
    }

    @Test
    fun transfersAndTicketAuthorsAreIndependentOfTheWorkerWhoOpenedTheTable() {
        val ana = TableWorker("3", "Ana")
        val opened = TableService().open(1, pablito)
        val visitId = requireNotNull(opened.openVisit(1)).id
        val time = Instant.parse("2026-11-11T15:45:00Z")
        val ordered = opened.add(visitId, cappuccino).sendLocally(visitId, juanito, time)
        assertEquals(pablito, ordered.openVisit(1)?.responsible)

        val transferred = ordered.transfer(visitId, ana, juanito, time)
        val retried = transferred.transfer(visitId, ana, juanito, time)
        val visit = requireNotNull(retried.openVisit(1))
        assertEquals(pablito, visit.openedBy)
        assertEquals(ana, visit.responsible)
        assertEquals(juanito.id, visit.dispatches.single().employeeId)
        assertEquals(listOf(ResponsibilityTransfer(pablito, ana, juanito, time)), visit.transfers)
        val closed = retried.close(visitId)
        assertEquals(closed, closed.transfer(visitId, juanito, ana, time))
    }

    @Test
    fun personalHistoryUsesLatestTableDispatchEvenIfAnotherWorkerSentIt() {
        val openedAt = Instant.parse("2026-11-11T15:00:00Z")
        val sentAt = Instant.parse("2026-11-11T15:30:00Z")
        val service = TableService().open(1, pablito, openedAt).open(1, juanito, sentAt)
        val visitId = requireNotNull(service.openVisit(1)).id
        val closed = service.add(visitId, cappuccino).sendLocally(visitId, juanito, sentAt)
            .add(visitId, brownie).sendLocally(visitId, pablito, sentAt.plusSeconds(60)).close(visitId)

        val orders = closed.ordersBy(juanito.id)
        assertEquals(listOf(1), orders.map { it.number })
        assertEquals(listOf(sentAt.plusSeconds(60)), orders.map { it.lastActivityAt })
        assertEquals(listOf(1), orders.map { it.table })
        assertEquals(RegisteredOrderStatus.CLOSED, orders.single().status)
    }

    @Test
    fun personalHistoryPrioritizesReadyProductsOnlyWhileTheVisitIsOpen() {
        val ready = brownie.copy(kitchenStatus = KitchenItemStatus.READY)
        val opened = TableService().open(1, pablito)
        val id = requireNotNull(opened.openVisit(1)).id
        val service = opened.add(id, ready).sendLocally(id, pablito, Instant.EPOCH)
        val order = service.ordersBy(pablito.id).single()
        assertEquals(RegisteredOrderStatus.READY, order.status)
        assertEquals(2L, order.readyQuantity)
        assertEquals(RegisteredOrderStatus.CLOSED, service.close(id).ordersBy(pablito.id).single().status)
    }

    @Test
    fun cartMergesIdenticalSelectionsAndKeepsDifferentCustomizationsSeparate() {
        val oatCappuccino = cappuccino.copy(options = listOf("Alto", "Avena"), unitPriceInCents = 1600)
        val cart = emptyList<OrderLine>().addCartLine(cappuccino).addCartLine(oatCappuccino).addCartLine(cappuccino)

        assertEquals(listOf(2, 1), cart.map { it.quantity })
        assertEquals(4400L, cart.sumOf { it.totalInCents })
        assertEquals(listOf(oatCappuccino), cart.adjustCartLine(cappuccino, -1).adjustCartLine(cappuccino, -1))
    }

    @Test
    fun adjustingTheCartDoesNotChangeRegisteredTicketsOrAnotherTable() {
        var service = TableService().open(1, pablito).open(2, juanito)
        val first = requireNotNull(service.openVisit(1)).id
        val second = requireNotNull(service.openVisit(2)).id
        service = service.add(first, cappuccino).sendLocally(first, pablito, Instant.EPOCH)
            .add(first, cappuccino).add(second, cappuccino)
            .adjustDraft(first, cappuccino, 1)

        assertEquals(2, service.openVisit(1)?.draft?.single()?.quantity)
        assertEquals(1, service.openVisit(2)?.draft?.single()?.quantity)
        assertEquals(1400L, service.openVisit(1)?.totalInCents)
    }

    @Test
    fun savingCartaChangesMergesWithTheLatestDraftOfTheOriginalTable() {
        val opened = TableService().open(1, pablito).open(2, juanito)
        val first = requireNotNull(opened.openVisit(1)).id
        val second = requireNotNull(opened.openVisit(2)).id
        val pending = listOf(cappuccino, brownie)
        val current = opened.add(first, cappuccino).add(second, brownie)

        val saved = current.addDraftLines(first, pending)

        assertEquals(listOf(cappuccino.copy(quantity = 2), brownie), saved.openVisit(1)?.draft)
        assertEquals(listOf(brownie), saved.openVisit(2)?.draft)
        assertEquals(emptyList<OrderDispatch>(), saved.openVisit(1)?.dispatches)
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            opened.close(first).open(1, pablito).addDraftLines(first, pending)
        }
    }
}
