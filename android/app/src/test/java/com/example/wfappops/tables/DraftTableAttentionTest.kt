package com.example.wfappops.tables

import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.orders.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DraftTableAttentionTest {
    private val opener = TableWorker("7", "Álvaro Gallegos")
    private val other = TableWorker("8", "Lucía Pérez")
    private val location = TeamLocation("Centro", "CTR", "10")
    private val table = AvailableTable("900", "M01", "1", 6)
    private val line = OrderLine("Espresso", 1, 800)
    private val time = Instant.parse("2026-11-24T21:30:00Z")
    private val key = TableReference(location.id, table.id)

    @Test
    fun firstProductOccupiesTableAndRegistersDraftWithItsCreatorBeforeKitchenDispatch() {
        val selected = TableService().openApiTable(location, table, other)
        val visit = selected.visits.single()
        assertTrue(selected.localTableAttentions().isEmpty())
        assertTrue(selected.registeredOrders().isEmpty())

        val saved = selected.add(visit.id, line, time, opener)
        val occupied = saved.localTableAttentions()[key] as TableAttention.Occupied
        assertEquals("Álvaro G.", occupied.attendant?.shortName)
        assertEquals(time, occupied.startedAt)
        assertEquals(visit.id, occupied.visitId)
        assertEquals(visit.table, occupied.internalTable)
        val order = saved.ordersBy(opener.id).single()
        assertEquals(1, order.number)
        assertEquals(time, order.startedAt)
        assertEquals(RegisteredOrderStatus.DRAFT, order.status)
        assertTrue(saved.visits.single().dispatches.isEmpty())
    }

    @Test
    fun editingTransferAndKitchenSendKeepOriginalCreatorNumberAndStart() {
        val selected = TableService().openApiTable(location, table, opener)
        val id = selected.visits.single().id
        val saved = selected.add(id, line, time, opener)
        val updated = saved.add(id, line, time.plusSeconds(30), other)
            .transfer(id, other, other, time.plusSeconds(40))
            .sendLocally(id, other, time.plusSeconds(60))
        assertEquals(saved.localTableAttentions(), updated.localTableAttentions())
        assertEquals(other, updated.visits.single().responsible)
        assertEquals(opener, updated.visits.single().openedBy)
        assertEquals(1, updated.visits.single().orderNumber)
        assertEquals(time, updated.registeredOrders().single().startedAt)
        assertEquals(setOf(opener.id, other.id), updated.registeredOrders().single().employeeIds)
    }

    @Test
    fun clearingOnlyDraftFreesTableAndNextAttentionGetsAnotherNumber() {
        val selected = TableService().openApiTable(location, table, opener)
        val id = selected.visits.single().id
        val saved = selected.add(id, line, time, opener)
        val emptied = saved.adjustDraft(id, line, -1)
        assertTrue(emptied.visits.single().isDiscardedDraft)
        assertEquals(1, emptied.visits.single().orderNumber)
        assertEquals(RegisteredOrderStatus.CANCELED, emptied.registeredOrders().single().status)
        assertNull(emptied.openVisit(saved.visits.single().table))
        assertEquals(TableAttention.Free, emptied.localTableAttentions()[key])
        val reopened = emptied.openApiTable(location, table, other)
        val nextId = reopened.openVisit(reopened.visits.last().table)!!.id
        val next = reopened.add(nextId, line, time.plusSeconds(120), other)
        assertNotEquals(id, nextId)
        assertEquals(2, next.visits.last().orderNumber)
        assertEquals("Lucía P.", (next.localTableAttentions()[key] as TableAttention.Occupied).attendant?.shortName)
        assertEquals(2, next.visits.size)
    }

    @Test
    fun removingAdditionalDraftKeepsSentProductsAndOccupancyIncludingCanceledDispatches() {
        val selected = TableService().openApiTable(location, table, opener)
        val id = selected.visits.single().id
        val sent = selected.add(id, line, time, opener).sendLocally(id, opener, time.plusSeconds(1))
        val extra = line.copy(product = "Otro producto")
        val withDraft = sent.add(id, extra, time.plusSeconds(2), opener)
        val removed = withDraft.adjustDraft(id, extra, -1)
        assertFalse(removed.visits.single().closed)
        assertEquals(sent.localTableAttentions(), removed.localTableAttentions())
        assertEquals(sent.visits.single().dispatches, removed.visits.single().dispatches)

        val canceled = withDraft.cancelLocalLines(id, 0, setOf(0), opener, time.plusSeconds(3))
            .adjustDraft(id, extra, -1).closeEmptyDraftOrders()
        assertFalse(canceled.visits.single().closed)
        assertTrue(canceled.visits.single().dispatches.single().isCanceled)
    }

    @Test
    fun partialRemovalAndEmptySelectionDoNotCancelAnAttention() {
        val selected = TableService().openApiTable(location, table, opener)
        assertEquals(selected, selected.closeEmptyDraftOrders())
        val id = selected.visits.single().id
        val saved = selected.add(id, line.copy(quantity = 2), time, opener)
        val partial = saved.adjustDraft(id, line, -1).closeEmptyDraftOrders()
        assertEquals(1, partial.visits.single().draft.single().quantity)
        assertEquals(saved.localTableAttentions(), partial.localTableAttentions())
        assertFalse(partial.visits.single().closed)
    }

    @Test
    fun repairsPreviouslyNumberedEmptyDraftsWithoutTouchingOtherVisitsOrReusingTheirNumbers() {
        val stale = TableVisit("stale", 1, openedBy = opener, openedAt = time,
            orderNumber = 5, orderStartedAt = time, remoteTableId = table.id, locationId = location.id)
        val selected = stale.copy(id = "selected", table = 2, orderNumber = null, remoteTableId = "901")
        val hasDraft = stale.copy(id = "draft", table = 3, draft = listOf(line), orderNumber = 6, remoteTableId = "902")
        val sent = stale.copy(id = "sent", table = 4, orderNumber = 7, remoteTableId = "903",
            dispatches = listOf(OrderDispatch(time, opener.name, listOf(line.asSent()), opener.id)))
        val service = TableService(listOf(stale, selected, hasDraft, sent))
        val repaired = service.closeEmptyDraftOrders()
        assertTrue(repaired.visits.first().isDiscardedDraft)
        assertEquals(listOf(selected, hasDraft, sent), repaired.visits.drop(1))
        assertEquals(repaired, repaired.closeEmptyDraftOrders())
        assertEquals(TableAttention.Free, repaired.localTableAttentions()[key])
        assertEquals(5, repaired.registeredOrders().first { it.visitId == stale.id }.number)
        val next = repaired.openApiTable(location, table, other)
        val added = next.add(next.visits.last().id, line, time.plusSeconds(1), other)
        assertEquals(8, added.visits.last().orderNumber)
    }

    @Test
    fun tableIdentityIncludesLocationAndUnknownTablesAreNeverReportedFree() {
        var service = TableService().openApiTable(location, table, opener)
            .openApiTable(location.copy(id = "11"), table, other)
        val first = service.visits.first().id
        val second = service.visits.last().id
        service = service.add(first, line, time, opener).add(second, line, time.plusSeconds(1), other)
        assertEquals(2, service.localTableAttentions().size)
        assertEquals("Álvaro G.", (service.localTableAttentions()[key] as TableAttention.Occupied).attendant?.shortName)
        assertEquals("Lucía P.", (service.localTableAttentions()[TableReference("11", table.id)] as TableAttention.Occupied).attendant?.shortName)
        assertNull(service.localTableAttentions()[TableReference("12", table.id)])
    }

    @Test
    fun oldDraftMigrationKeepsStoredTimeAndDoesNotAllocateAgain() {
        val old = TableVisit("old", 1, draft = listOf(line), openedBy = opener, openedAt = time,
            remoteTableId = table.id, locationId = location.id)
        val migrated = TableService(listOf(old)).ensureDraftOrders()
        assertEquals(1, migrated.visits.single().orderNumber)
        assertEquals(time, (migrated.localTableAttentions()[key] as TableAttention.Occupied).startedAt)
        assertEquals(migrated, migrated.ensureDraftOrders())
        assertEquals(old.draft, migrated.visits.single().draft)
        val noTime = TableService(listOf(old.copy(openedAt = null))).ensureDraftOrders()
        assertNull((noTime.localTableAttentions()[key] as TableAttention.Occupied).startedAt)
    }
}
