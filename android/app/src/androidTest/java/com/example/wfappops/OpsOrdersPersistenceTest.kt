package com.example.wfappops

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.orders.data.*
import com.example.wfappops.orders.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID

class OpsOrdersPersistenceTest {
    @Test fun restartRetainsFulfillmentAndClosureDecisions() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-fulfillment-${UUID.randomUUID()}"
        try {
            val orderId = UUID.randomUUID().toString()
            val unitId = UUID.randomUUID().toString()
            val unit = org.json.JSONObject().put("id", unitId).put("order", org.json.JSONObject().put("id", orderId))
                .put("round", org.json.JSONObject().put("id", orderId)).put("order_item_status", "sent")
                .put("preparation_status", "queued").put("delivery_status", "pending").put("fulfillment_version", 3)
                .put("capabilities", org.json.JSONObject().put("can_start", true)).toString()
            val line = OrderLine("Café", 1, 1400, kitchenStatus = KitchenItemStatus.SENT, remoteUnitIds = listOf(unitId))
            val visit = TableVisit("visit", 1, apiBacked = true, remoteOrderId = orderId, remoteVersion = 8,
                remoteFulfillment = mapOf(unitId to unit))
            val operation = capturePreparation(visit, line, FulfillmentAction.START)
            TableStore(app, namespace).update { TableService(listOf(visit.copy(pendingPreparation = operation.encode()))) }
            val restored = TableStore(app, namespace).read().visits.single()
            assertEquals(operation, PreparationCommand.decode(restored.pendingPreparation!!))
            assertEquals(visit.remoteFulfillment, restored.remoteFulfillment)
            val close = captureClosure(ClosureReadiness(orderId, 8, true, emptyList()))
            TableStore(app, namespace).update { TableService(listOf(restored.copy(pendingPreparation = null, pendingClose = close))) }
            assertEquals(close, TableStore(app, namespace).read().visits.single().pendingClose)
        } finally {
            File(app.noBackupFilesDir, "ops-mesas-${Uri.encode(namespace)}.json").delete()
        }
    }
    @Test fun restartRetainsExactCancellationDecision() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-cancel-units-${UUID.randomUUID()}"
        try {
            val visit = TableVisit("visit", 1, apiBacked = true, remoteOrderId = UUID.randomUUID().toString(), remoteVersion = 4,
                dispatches = listOf(OrderDispatch(Instant.EPOCH, "Colaborador", listOf(OrderLine("Café", 1, 1400,
                    kitchenStatus = KitchenItemStatus.READY, remoteUnitIds = listOf(UUID.randomUUID().toString()))))))
            val decision = captureUnitCancellation(visit, 0, setOf(0), "Cliente desistió", 4)
            TableStore(app, namespace).update { TableService(listOf(visit.copy(pendingCancellation = decision))) }
            assertEquals(decision, TableStore(app, namespace).read().visits.single().pendingCancellation)
        } finally {
            File(app.noBackupFilesDir, "ops-mesas-${Uri.encode(namespace)}.json").delete()
        }
    }
    @Test fun restartRetainsPendingCaptureServerReferencesAndUnsentRemainder() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-ops-orders-${UUID.randomUUID()}"
        try {
            val line = OrderLine("Producto de contrato", 3, 1650,
                productId = UUID.randomUUID().toString(), variantId = UUID.randomUUID().toString(), locationId = "27")
            val draft = TableVisit(UUID.randomUUID().toString(), 1, apiBacked = true,
                remoteTableId = "54", locationId = "27", draft = listOf(line))
            val capture = captureOrderSend(draft, mapOf(0 to 2))
            val pending = draft.copy(pendingSend = capture.encode())
            TableStore(app, namespace).update { TableService(listOf(pending)) }
            val restored = TableStore(app, namespace).read().visits.single()
            assertEquals(pending.copy(localDraftNumber = 1), restored)
            assertEquals(capture, PendingOrderSend.decode(restored.pendingSend!!))
            val remoteLine = line.copy(quantity = 2, kitchenStatus = KitchenItemStatus.SENT,
                remoteUnitIds = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString()), remoteLineNumber = 1)
            val remote = restored.copy(pendingSend = null, localDraftNumber = null, draft = listOf(line.copy(quantity = 1)),
                orderNumber = 12, orderStartedAt = Instant.EPOCH, openedAt = Instant.EPOCH,
                remoteOrderId = UUID.randomUUID().toString(), remoteVersion = 3, remoteStatus = "open",
                remoteTotalMinor = 3300, remoteUnitsCount = 2, remoteDetailLoaded = true, businessDate = "2026-11-25",
                dispatches = listOf(OrderDispatch(Instant.EPOCH, "Colaborador del API", listOf(remoteLine),
                    remoteId = UUID.randomUUID().toString(), sequence = 2, clientRoundId = capture.roundId, note = "Servir junto")))
            TableStore(app, namespace).update { TableService(listOf(remote)) }
            assertEquals(remote, TableStore(app, namespace).read().visits.single())
            var displayedLine = remoteLine
            listOf(KitchenItemStatus.IN_PREPARATION, KitchenItemStatus.READY, KitchenItemStatus.DELIVERED).forEachIndexed { index, status ->
                val simulated = TableStore(app, namespace).update { source -> source.advanceLocalKitchenStatus(
                    DispatchLineKey(remote.id, 0, 0), displayedLine, remote.remoteVersion,
                    Instant.EPOCH.plusSeconds(60L + index).plusNanos(123456789)) }
                val savedSimulation = TableStore(app, namespace).read()
                assertEquals(simulated, savedSimulation)
                assertEquals(KitchenItemStatus.SENT, savedSimulation.visits.single().dispatches.single().lines.single().dispatchStatus)
                displayedLine = simulated.withLocalKitchenStatuses().visits.single().dispatches.single().lines.single()
                assertEquals(status, displayedLine.dispatchStatus)
            }
        } finally {
            File(app.noBackupFilesDir, "ops-mesas-${Uri.encode(namespace)}.json").delete()
        }
    }
}
