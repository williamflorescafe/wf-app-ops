package com.example.wfappops.orders.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(listOf(cappuccino.asSent()), visit.dispatches.first().lines)
        assertEquals(juanito, visit.dispatches.first().canceledBy)
        assertEquals(time, visit.dispatches.first().canceledAt)
        assertEquals(brownie.totalInCents, visit.totalInCents)
        assertEquals(canceled, canceled.cancelLocalDispatch(id, 0, pablito, time.plusSeconds(1)))
    }

    @Test
    fun cancelingSelectedLinesPreservesTheRestOfTheDispatchAndItsHistory() {
        val opened = TableService().open(1, pablito)
        val id = requireNotNull(opened.openVisit(1)).id
        val sent = opened.add(id, cappuccino).add(id, brownie).sendLocally(id, pablito, Instant.EPOCH)
        val canceledAt = Instant.EPOCH.plusSeconds(20)
        val partiallyCanceled = sent.cancelLocalLines(id, 0, setOf(0), juanito, canceledAt)
        val visit = requireNotNull(partiallyCanceled.openVisit(1))
        val dispatch = visit.dispatches.single()

        assertEquals(listOf(cappuccino.product, brownie.product), dispatch.lines.map { it.product })
        assertEquals(juanito, dispatch.lines.first().canceledBy)
        assertEquals(canceledAt, dispatch.lines.first().canceledAt)
        assertEquals(brownie.totalInCents, visit.totalInCents)
        assertFalse(dispatch.isCanceled)
        assertEquals(listOf(brownie.asSent()), partiallyCanceled.kitchenQueue().inProgress.single().lines.map { it.line })
        assertEquals(canceledAt, partiallyCanceled.ordersBy(pablito.id).single().lastActivityAt)
        assertEquals(partiallyCanceled, partiallyCanceled.cancelLocalLines(id, 0, setOf(0), pablito, canceledAt.plusSeconds(1)))

        val fullyCanceled = partiallyCanceled.cancelLocalLines(id, 0, setOf(1), juanito, canceledAt.plusSeconds(2))
        assertTrue(fullyCanceled.openVisit(1)?.dispatches?.single()?.isCanceled == true)
        assertEquals(KitchenQueue(emptyList(), emptyList()), fullyCanceled.kitchenQueue())
        assertEquals(0L, fullyCanceled.openVisit(1)?.totalInCents)
    }

    @Test
    fun cancellationOnlyAppliesToLinesStillWaitingForPreparation() {
        val opened = TableService().open(1, pablito)
        val id = requireNotNull(opened.openVisit(1)).id
        val sentAt = Instant.EPOCH
        val changedAt = sentAt.plusSeconds(10)
        val canceledAt = changedAt.plusSeconds(10)
        val ready = cappuccino.copy(product = "Latte", kitchenStatus = KitchenItemStatus.READY)
        val delivered = cappuccino.copy(product = "Americano", kitchenStatus = KitchenItemStatus.DELIVERED)
        val sent = opened.add(id, cappuccino).add(id, ready).add(id, delivered).add(id, brownie)
            .sendLocally(id, pablito, sentAt)
        val preparing = sent.advanceKitchenLine(DispatchLineKey(id, 0, 0), KitchenItemStatus.SENT, changedAt)

        assertEquals(preparing, preparing.cancelLocalDispatch(id, 0, juanito, canceledAt))
        assertEquals(preparing, preparing.cancelLocalLines(id, 0, setOf(0, 1, 2), juanito, canceledAt))

        val mixed = preparing.cancelLocalLines(id, 0, setOf(0, 1, 2, 3), juanito, canceledAt)
        val visit = requireNotNull(mixed.openVisit(1))
        assertEquals(
            listOf(KitchenItemStatus.IN_PREPARATION, KitchenItemStatus.READY, KitchenItemStatus.DELIVERED, KitchenItemStatus.CANCELED),
            visit.dispatches.single().lines.map { it.kitchenStatus }
        )
        assertEquals(cappuccino.totalInCents + ready.totalInCents + delivered.totalInCents, visit.totalInCents)
    }

    @Test
    fun kitchenTimestampsFollowEachTransitionAndAStaleTapCannotSkipAStatus() {
        val opened = TableService().open(1, pablito)
        val id = requireNotNull(opened.openVisit(1)).id
        val sentAt = Instant.EPOCH.plusSeconds(10)
        val receivedAt = sentAt.plusSeconds(2)
        val startedAt = receivedAt.plusSeconds(5)
        val readyAt = startedAt.plusSeconds(10)
        val deliveredAt = readyAt.plusSeconds(2)
        val sent = opened.add(id, cappuccino).sendLocally(id, pablito, sentAt, receivedAt)
        val ticket = sent.kitchenQueue().inProgress.single()
        val key = ticket.lines.single().key
        assertEquals(KitchenItemStatus.SENT, ticket.lines.single().line.kitchenStatus)
        assertEquals(RegisteredOrderStatus.SENT, sent.ordersBy(pablito.id).single().status)
        assertEquals(sentAt, ticket.sentAt)
        assertEquals(receivedAt, ticket.receivedAt)
        assertEquals(sent, sent.deliverLine(key, startedAt))

        val preparing = sent.advanceKitchenLine(key, KitchenItemStatus.SENT, startedAt)
        assertEquals(preparing, preparing.advanceKitchenLine(key, KitchenItemStatus.SENT, startedAt.plusSeconds(1)))
        val ready = preparing.advanceKitchenLine(key, KitchenItemStatus.IN_PREPARATION, readyAt)
        assertEquals(ready, ready.advanceKitchenLine(key, KitchenItemStatus.READY, deliveredAt))
        assertEquals(RegisteredOrderStatus.READY, ready.ordersBy(pablito.id).single().status)
        val kitchen = ready.kitchenQueue()
        assertTrue(kitchen.inProgress.isEmpty())
        val confirmed = kitchen.confirmed.single().lines.single()
        assertEquals(key, confirmed.key)
        assertEquals(readyAt, confirmed.line.readyAt)

        val delivered = ready.deliverLine(key, deliveredAt)
        val line = delivered.openVisit(1)?.dispatches?.single()?.lines?.single()
        assertEquals(KitchenItemStatus.DELIVERED, line?.kitchenStatus)
        assertEquals(startedAt, line?.preparationStartedAt)
        assertEquals(readyAt, line?.readyAt)
        assertEquals(deliveredAt, line?.deliveredAt)
        assertEquals(KitchenQueue(emptyList(), emptyList()), delivered.kitchenQueue())
        assertEquals(RegisteredOrderStatus.DELIVERED, delivered.ordersBy(pablito.id).single().status)
        assertEquals(cappuccino.totalInCents, delivered.openVisit(1)?.totalInCents)
        val closed = ready.close(id)
        assertEquals(closed, closed.deliverLine(key, deliveredAt))
    }

    @Test
    fun orderLineAdvancesIndividuallyAndRepeatedEventsPreserveTimestamps() {
        val opened = TableService().open(1, pablito)
        val id = requireNotNull(opened.openVisit(1)).id
        val sentAt = Instant.EPOCH
        var service = opened.add(id, cappuccino).add(id, brownie).sendLocally(id, pablito, sentAt)
        val key = DispatchLineKey(id, 0, 0)
        val states = listOf(KitchenItemStatus.SENT, KitchenItemStatus.IN_PREPARATION, KitchenItemStatus.READY)
        val transitions = states + KitchenItemStatus.DELIVERED
        states.forEachIndexed { index, expected ->
            val changedAt = sentAt.plusSeconds(index.toLong() + 1)
            service = service.advanceOrderLine(key, expected, changedAt)
            assertEquals(transitions[index + 1], service.openVisit(1)?.dispatches?.single()?.lines?.first()?.dispatchStatus)
            assertEquals(service, service.advanceOrderLine(key, expected, changedAt.plusSeconds(1)))
            assertEquals(KitchenItemStatus.SENT, service.openVisit(1)?.dispatches?.single()?.lines?.last()?.dispatchStatus)
        }
        val line = requireNotNull(service.openVisit(1)).dispatches.single().lines.first()
        assertEquals(sentAt.plusSeconds(1), line.preparationStartedAt)
        assertEquals(sentAt.plusSeconds(2), line.readyAt)
        assertEquals(sentAt.plusSeconds(3), line.deliveredAt)
        assertFalse(requireNotNull(service.openVisit(1)).canSendToCashier)
        val canceled = service.cancelLocalLines(id, 0, setOf(1), pablito, sentAt.plusSeconds(4))
        assertTrue(requireNotNull(canceled.openVisit(1)).canSendToCashier)
        assertEquals(canceled, canceled.advanceOrderLine(DispatchLineKey(id, 0, 1), KitchenItemStatus.SENT, sentAt.plusSeconds(5)))
        val closed = canceled.sendToCashier(id, pablito, sentAt.plusSeconds(6))
        assertEquals(closed, closed.advanceOrderLine(key, KitchenItemStatus.READY, sentAt.plusSeconds(7)))
    }

    @Test
    fun readyNoticesAccumulateAcrossTablesForTheEmployeeWhoSentEachProduct() {
        val sameName = juanito.copy(name = pablito.name)
        val opened = TableService().open(1, sameName).open(2, pablito)
        val first = requireNotNull(opened.openVisit(1)).id
        val second = requireNotNull(opened.openVisit(2)).id
        val sent = opened.add(first, cappuccino).sendLocally(first, pablito, Instant.EPOCH)
            .add(second, brownie).sendLocally(second, pablito, Instant.EPOCH.plusSeconds(1))
            .add(second, cappuccino).sendLocally(second, sameName, Instant.EPOCH.plusSeconds(2))
        val firstLine = DispatchLineKey(first, 0, 0)
        val secondLine = DispatchLineKey(second, 0, 0)
        val otherLine = DispatchLineKey(second, 1, 0)
        val start = Instant.EPOCH.plusSeconds(3)
        val preparing = sent.advanceKitchenLine(firstLine, KitchenItemStatus.SENT, start)
            .advanceKitchenLine(secondLine, KitchenItemStatus.SENT, start)
            .advanceKitchenLine(otherLine, KitchenItemStatus.SENT, start)
        assertTrue(preparing.readyNoticesFor(pablito.id).isEmpty())

        val ready = preparing.advanceKitchenLine(secondLine, KitchenItemStatus.IN_PREPARATION, start.plusSeconds(1))
            .advanceKitchenLine(otherLine, KitchenItemStatus.IN_PREPARATION, start.plusSeconds(2))
            .advanceKitchenLine(firstLine, KitchenItemStatus.IN_PREPARATION, start.plusSeconds(3))
        val messages = ready.readyNoticesFor(pablito.id)
        assertEquals(listOf(secondLine, firstLine), messages.map { it.key })
        assertEquals(listOf(2, 1), messages.map { it.table })
        assertEquals(listOf(2, 1), messages.map { it.quantity })
        assertEquals(listOf(otherLine), ready.readyNoticesFor(sameName.id).map { it.key })
        assertEquals(messages.takeLast(1), ready.deliverLine(secondLine, start.plusSeconds(4)).readyNoticesFor(pablito.id))
        assertEquals(messages.take(1), ready.close(first).readyNoticesFor(pablito.id))
    }

    @Test
    fun cashierHandoffClosesOnlyResolvedOrdersAndPreservesTheCommentAndHistory() {
        val opened = TableService().open(1, pablito)
        val id = requireNotNull(opened.openVisit(1)).id
        val sentAt = Instant.EPOCH
        val deliveryTime = sentAt.plusSeconds(20)
        val sent = opened.add(id, cappuccino.copy(kitchenStatus = KitchenItemStatus.READY)).add(id, brownie)
            .sendLocally(id, pablito, sentAt)
            .add(id, cappuccino).sendLocally(id, juanito, sentAt.plusSeconds(1))
            .saveComment(id, "  Cobrar por separado  ")
        assertEquals(sent, sent.deliverDispatch(id, 0, deliveryTime))
        assertEquals(sent, sent.sendToCashier(id, pablito, deliveryTime))

        val key = DispatchLineKey(id, 0, 1)
        val ready = sent.advanceKitchenLine(key, KitchenItemStatus.SENT, sentAt.plusSeconds(5))
            .advanceKitchenLine(key, KitchenItemStatus.IN_PREPARATION, sentAt.plusSeconds(10))
        assertEquals(listOf(juanito.id), ready.kitchenQueue().inProgress.map { it.employeeId })
        assertEquals(listOf(pablito.id), ready.kitchenQueue().confirmed.map { it.employeeId })
        assertEquals(ready, ready.sendToCashier(id, pablito, deliveryTime))
        assertTrue(ready.openVisit(1)?.dispatches?.first()?.canDeliver == true)

        val delivered = ready.deliverDispatch(id, 0, deliveryTime)
        val visit = requireNotNull(delivered.openVisit(1))
        assertTrue(visit.dispatches.first().isDelivered)
        assertTrue(visit.dispatches.first().lines.all { it.deliveredAt == deliveryTime })
        assertEquals(ready.openVisit(1)?.totalInCents, visit.totalInCents)
        assertEquals(ready.openVisit(1)?.dispatches?.last(), visit.dispatches.last())
        assertEquals(2, delivered.kitchenQueue().inProgress.single().ordinal)
        assertEquals(delivered, delivered.deliverDispatch(id, 0, deliveryTime.plusSeconds(1)))

        val resolved = delivered.cancelLocalDispatch(id, 1, juanito, deliveryTime.plusSeconds(1))
        val withDraft = resolved.add(id, cappuccino)
        assertEquals(withDraft, withDraft.sendToCashier(id, pablito, deliveryTime))
        val handoffTime = deliveryTime.plusSeconds(5)
        val closed = resolved.sendToCashier(id, pablito, handoffTime)
        val saved = closed.visits.single()
        assertTrue(saved.closed)
        assertNull(closed.openVisit(1))
        assertEquals("Cobrar por separado", saved.comment)
        assertEquals(CashierHandoff(handoffTime, pablito), saved.cashierHandoff)
        assertEquals(cappuccino.totalInCents + brownie.totalInCents, saved.totalInCents)
        assertEquals(resolved.visits.single().dispatches, saved.dispatches)
        assertEquals(RegisteredOrderStatus.CLOSED, closed.ordersBy(pablito.id).single().status)
        assertEquals(closed, closed.sendToCashier(id, juanito, handoffTime.plusSeconds(1)))
    }

    @Test
    fun servedDispatchMayContainCanceledLinesButNoUnfinishedPlates() {
        val served = cappuccino.copy(kitchenStatus = KitchenItemStatus.DELIVERED)
        val canceled = brownie.copy(kitchenStatus = KitchenItemStatus.CANCELED)
        val dispatch = OrderDispatch(Instant.EPOCH, pablito.name, listOf(served, canceled))

        assertTrue(dispatch.isDelivered)
        assertFalse(dispatch.copy(lines = dispatch.lines + cappuccino.copy(kitchenStatus = KitchenItemStatus.IN_PREPARATION)).isDelivered)
        assertFalse(dispatch.copy(lines = listOf(canceled)).isDelivered)
    }

    @Test
    fun firstDraftProductAssignsIdentityAndLaterDispatchesKeepIt() {
        val started = TableService().open(4, pablito).open(5, juanito)
        val first = requireNotNull(started.openVisit(4))
        val second = requireNotNull(started.openVisit(5))
        assertNull(first.orderNumber)
        assertNull(first.orderStartedAt)
        val firstTime = Instant.parse("2026-11-11T15:30:00Z")
        val secondTime = firstTime.plusSeconds(60)
        val draft = started.add(second.id, brownie, firstTime, juanito)
        assertEquals(1, draft.openVisit(5)?.orderNumber)
        assertEquals(firstTime, draft.openVisit(5)?.orderStartedAt)
        assertTrue(draft.openVisit(5)!!.dispatches.isEmpty())
        assertEquals(RegisteredOrderStatus.DRAFT, draft.ordersBy(juanito.id).single().status)
        val sent = draft.sendLocally(second.id, juanito, secondTime)
            .add(first.id, cappuccino, firstTime, pablito).sendLocally(first.id, pablito, firstTime)
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
        assertEquals(listOf(annotated.asSent(), cappuccino.asSent()), sent.openVisit(1)?.dispatches?.single()?.lines)
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
        assertEquals(listOf(cappuccino.asSent()), service.visits.first().dispatches.single().lines)
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
    fun kitchenQueueKeepsActiveLinesInSendOrderAndPreservesDispatchNumbers() {
        val opened = TableService().open(1, pablito).open(2, juanito).open(3, pablito)
        val first = requireNotNull(opened.openVisit(1)).id
        val second = requireNotNull(opened.openVisit(2)).id
        val closed = requireNotNull(opened.openVisit(3)).id
        val pending = cappuccino.copy(note = "Sin espuma")
        val preparing = brownie.copy(kitchenStatus = KitchenItemStatus.IN_PREPARATION)
        val served = brownie.copy(kitchenStatus = KitchenItemStatus.DELIVERED)
        val ready = cappuccino.copy(kitchenStatus = KitchenItemStatus.READY, quantity = 3)
        val service = opened
            .add(first, brownie).sendLocally(first, pablito, Instant.EPOCH)
            .cancelLocalDispatch(first, 0, pablito, Instant.EPOCH.plusSeconds(1))
            .add(closed, cappuccino).sendLocally(closed, pablito, Instant.EPOCH.plusSeconds(2)).close(closed)
            .add(second, preparing).sendLocally(second, juanito, Instant.EPOCH.plusSeconds(3))
            .add(first, served).add(first, pending).add(first, ready).sendLocally(first, pablito, Instant.EPOCH.plusSeconds(4))
            .add(first, served).sendLocally(first, pablito, Instant.EPOCH.plusSeconds(5))
            .add(first, brownie)

        val kitchen = service.kitchenQueue()
        val queue = kitchen.inProgress
        assertEquals(listOf(2, 1), queue.map { it.table })
        assertEquals(listOf(1, 2), queue.map { it.ordinal })
        assertEquals(listOf(listOf(preparing), listOf(pending.asSent())), queue.map { dispatch -> dispatch.lines.map { it.line } })
        assertEquals(DispatchLineKey(first, 1, 1), queue.last().lines.single().key)
        val confirmed = kitchen.confirmed.single()
        assertEquals(2, confirmed.ordinal)
        assertEquals(DispatchLineKey(first, 1, 2), confirmed.lines.single().key)
        assertEquals(3, confirmed.lines.single().line.quantity)
        assertTrue(kitchen.forEmployee(juanito.id).confirmed.isEmpty())
        assertEquals(listOf(confirmed), kitchen.forEmployee(pablito.id).confirmed)
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
