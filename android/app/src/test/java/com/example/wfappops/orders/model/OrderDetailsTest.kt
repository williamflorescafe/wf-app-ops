package com.example.wfappops.orders.model

import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.tables.AvailableTable
import org.junit.Assert.*
import org.junit.Test

class OrderDetailsTest {
    @Test fun productsCanBeSentWithoutTableAndAssignedLaterWithoutLosingTheirState() {
        val created = TableService().createOrder(worker)
        val visit = created.visits.single()
        val sent = created.add(visit.id, OrderLine("Café", 2, 100))
            .sendLocally(visit.id, worker, java.time.Instant.now())
        assertNull(sent.visits.single().remoteTableId)
        assertTrue(sent.visits.single().draft.isEmpty())
        assertEquals(2, sent.visits.single().dispatches.single().lines.single().quantity)
        val assigned = sent.assignApiTable(visit.id, location, firstTable).visits.single()
        assertEquals(visit.id, assigned.id)
        assertEquals(visit.orderNumber, assigned.orderNumber)
        assertEquals(KitchenItemStatus.SENT, assigned.dispatches.single().lines.single().kitchenStatus)
        assertEquals(firstTable.id, assigned.remoteTableId)
    }
    private val worker = TableWorker("1", "Álvaro")
    private val location = TeamLocation("San Borja", "SB", "10")
    private val firstTable = AvailableTable("api-1", "M01", "1", 4)
    private val secondTable = AvailableTable("api-2", "M02", "2", 4)

    @Test fun numberedOrderExistsBeforeSelectingTableAndKeepsItsIdentityWhenMoved() {
        var service = TableService().createOrder(worker)
        val visit = service.visits.single()
        assertEquals(1, visit.orderNumber)
        assertNull(visit.remoteTableId)
        assertFalse(service.closeEmptyDraftOrders().visits.single().closed)
        service = service.assignApiTable(visit.id, location, firstTable)
            .add(visit.id, OrderLine("Café", 2, 100))
            .assignApiTable(visit.id, location, secondTable)
        val moved = service.visits.single()
        assertEquals(visit.id, moved.id)
        assertEquals(1, moved.orderNumber)
        assertEquals("api-2", moved.remoteTableId)
        assertEquals(2, moved.draft.single().quantity)
        assertEquals(worker, moved.responsible)
        assertFalse(moved.numberReserved)
        val next = service.createOrder(worker).visits.last()
        assertEquals(2, next.orderNumber)
    }

    @Test fun assigningOccupiedTableCannotMergeOrOverwriteAnotherOrder() {
        val first = TableService().createOrder(worker)
        val firstId = first.visits.single().id
        val occupied = first.assignApiTable(firstId, location, firstTable).add(firstId, OrderLine("Café", 1, 100))
        val another = occupied.createOrder(worker)
        val secondId = another.visits.last().id
        assertTrue(runCatching { another.assignApiTable(secondId, location, firstTable) }.isFailure)
        assertEquals(2, another.visits.size)
        assertEquals(1, another.visits.first().draft.single().quantity)
    }
}
