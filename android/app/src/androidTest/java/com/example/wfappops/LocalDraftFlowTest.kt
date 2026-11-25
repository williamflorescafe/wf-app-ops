package com.example.wfappops

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.ActiveOpsAccess
import com.example.wfappops.auth.OpsAccess
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.data.TableStore
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.tables.AvailableTable
import com.example.wfappops.tables.TableAttention
import com.example.wfappops.tables.TableReference
import com.example.wfappops.tables.localTableAttentions
import com.example.wfappops.orders.model.RegisteredOrderStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDraftFlowTest {
    @Test
    fun draftPersistsWithProductsAndIsIsolatedFromOtherEmployeesAndRemoteOrders() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val businessId = "draft-test-${UUID.randomUUID()}"
        val previousAccess = ActiveOpsAccess.current
        val worker = TableWorker("worker-a", "Álvaro Gallegos")
        ActiveOpsAccess.current = OpsAccess(businessId)
        try {
            val source = OrderRepository(app, businessId, localDraftEmployeeId = worker.id)
            val catalog = source.observeCatalog().first().getOrThrow()
            val product = catalog.flatMap { it.sections }.flatMap { it.products }.first()
            val remoteTable = AvailableTable("table-api-99999999999", "T-9", "Mesa terraza", 6)
            val visit = source.update {
                it.openApiTable(TeamLocation("San Borja", "SB", "10"), remoteTable, worker)
            }.visits.single()
            val line = OrderLine(product.name, 1, product.priceInCents)
            val start = Instant.parse("2026-11-24T21:30:00Z")
            val observer = OrderRepository(app, businessId, localDraftEmployeeId = worker.id)
            val observedAttention = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    observer.observe { it.localTableAttentions() }.first {
                        it[TableReference("10", remoteTable.id)] is TableAttention.Occupied
                    }
                }
            }
            source.update { it.add(visit.id, line, start, worker) }
            val attention = observedAttention.await()[TableReference("10", remoteTable.id)] as TableAttention.Occupied
            assertEquals("Álvaro G.", attention.attendant?.shortName)
            assertEquals(start, attention.startedAt)
            assertEquals(visit.id, attention.visitId)
            source.update { it.adjustDraft(visit.id, line, 1) }
            source.update { it.editDraftNote(visit.id, line, "Servir juntos") }

            val reopened = OrderRepository(app, businessId, localDraftEmployeeId = worker.id)
                .read { it.openVisit(visit.table)!! }
            assertEquals(visit.id, reopened.id)
            assertEquals(2, reopened.draft.single().quantity)
            assertEquals("Servir juntos", reopened.draft.single().note)
            assertEquals(remoteTable.id, reopened.remoteTableId)
            assertEquals(remoteTable.name, reopened.tableName)
            assertEquals("10", reopened.locationId)
            assertTrue(reopened.dispatches.isEmpty())
            assertEquals(1, reopened.orderNumber)
            assertEquals(start, reopened.orderStartedAt)
            assertEquals(worker, reopened.openedBy)
            assertEquals(RegisteredOrderStatus.DRAFT, observer.read { it.ordersBy(worker.id).single().status })
            assertFalse(reopened.canSendToCashier)
            assertTrue(OrderRepository(app, businessId, localDraftEmployeeId = "worker-b").read { it.visits.isEmpty() })
            assertTrue(runCatching { OrderRepository(app, businessId).read { it.visits } }.isFailure)

            val observedFree = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    observer.observe { it.localTableAttentions() }.first {
                        it[TableReference("10", remoteTable.id)] == TableAttention.Free
                    }
                }
            }
            source.update { it.adjustDraft(visit.id, reopened.draft.single(), -2) }
            observedFree.await()
            val afterRemoval = OrderRepository(app, businessId, localDraftEmployeeId = worker.id).read { it }
            assertTrue(afterRemoval.visits.single().isDiscardedDraft)
            assertEquals(1, afterRemoval.visits.single().orderNumber)
            assertEquals(RegisteredOrderStatus.CANCELED, afterRemoval.registeredOrders().single().status)
            assertTrue(afterRemoval.visits.single().draft.isEmpty())
            val newVisit = source.update {
                it.openApiTable(TeamLocation("San Borja", "SB", "10"), remoteTable, worker)
            }.visits.last()
            assertFalse(newVisit.id == visit.id)
            val nextOrder = source.update { it.add(newVisit.id, line, start.plusSeconds(60), worker) }
            assertEquals(2, nextOrder.visits.last().orderNumber)

            ActiveOpsAccess.current = OpsAccess("another-business")
            assertTrue(runCatching { source.read { it.visits } }.isFailure)
        } finally {
            ActiveOpsAccess.current = previousAccess
            // Solo archivos del negocio único creado por esta prueba.
            app.noBackupFilesDir.listFiles().orEmpty().filter { it.name.contains(businessId) }
                .forEach { it.delete() }
        }
    }

    @Test
    fun readingRepairsAnEmptyNumberedAttentionSavedByThePreviousVersion() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val businessId = "draft-repair-${UUID.randomUUID()}"
        val previousAccess = ActiveOpsAccess.current
        val worker = TableWorker("worker-a", "Colaborador")
        ActiveOpsAccess.current = OpsAccess(businessId)
        try {
            val scope = "local-drafts-v1-${businessId.length}:$businessId-${worker.id}"
            val stale = TableVisit("stale", 3, orderNumber = 5, openedBy = worker,
                orderStartedAt = Instant.EPOCH, remoteTableId = "300", locationId = "10")
            val selected = stale.copy(id = "selected", table = 4, orderNumber = null, remoteTableId = "400")
            val store = TableStore(app, scope)
            store.update { TableService(listOf(stale, selected)) }
            val source = OrderRepository(app, businessId, localDraftEmployeeId = worker.id)
            val repaired = source.read { it }
            assertTrue(repaired.visits.first().isDiscardedDraft)
            assertEquals(selected, repaired.visits.last())
            assertEquals(TableAttention.Free, repaired.localTableAttentions()[TableReference("10", "300")])
            assertEquals(repaired, TableStore(app, scope).read())
            assertEquals(repaired, source.read { it })
        } finally {
            ActiveOpsAccess.current = previousAccess
            app.noBackupFilesDir.listFiles().orEmpty().filter { it.name.contains(businessId) }.forEach { it.delete() }
        }
    }
}
