package com.example.wfappops.orders.model

import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.tables.AvailableTable
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ApiTableOrderTest {
    @Test
    fun remoteIdentitySurvivesManualLifecycleAndDoesNotMergeLocations() {
        val worker = TableWorker("7", "Álvaro")
        val location = TeamLocation("San Borja", "SB", "10")
        val table = AvailableTable("remote-id-900000000000", "M01", "Mesa terraza", 6)
        val opened = TableService().openApiTable(location, table, worker)
        val visit = opened.visits.single()
        assertEquals(table.id, visit.remoteTableId)
        assertEquals(opened, opened.openApiTable(location, table, worker))
        val other = opened.openApiTable(location.copy(id = "11"), table, worker)
        assertEquals(2, other.visits.size)
        assertNotEquals(other.visits[0].table, other.visits[1].table)

        var service = opened.add(visit.id, OrderLine("Cappuccino", 1, 1400))
            .sendLocally(visit.id, worker, Instant.now())
        val key = DispatchLineKey(visit.id, 0, 0)
        service = service.advanceOrderLine(key, KitchenItemStatus.SENT, Instant.now())
        service = service.advanceOrderLine(key, KitchenItemStatus.IN_PREPARATION, Instant.now())
        assertEquals(KitchenItemStatus.READY, service.visits.single().dispatches.single().lines.single().kitchenStatus)
        service = service.advanceOrderLine(key, KitchenItemStatus.READY, Instant.now())
        assertTrue(service.visits.single().canSendToCashier)
        assertEquals(table.name, service.registeredOrders().single().tableName)
        service = service.sendToCashier(visit.id, worker, Instant.now())
        assertTrue(service.visits.single().closed)
        assertEquals(table.id, service.visits.single().remoteTableId)
    }
}
