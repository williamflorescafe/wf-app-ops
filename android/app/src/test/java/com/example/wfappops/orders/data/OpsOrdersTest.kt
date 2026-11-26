package com.example.wfappops.orders.data

import com.example.wfappops.orders.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class OpsOrdersTest {
    private val client = "123e4567-e89b-42d3-a456-426614174000"
    private val order = "019badb1-ace0-7c10-834a-53c11ab40010"
    private val round = "019badb1-ace0-7c10-834a-53c11ab40011"
    private val variant = "019badb1-ace0-7c10-834a-53c11ab40012"
    private val worker = TableWorker("42", "Persona actual")
    private fun line(quantity: Int = 3) = OrderLine("Café", quantity, 1650,
        note = "Sin azúcar", productId = variant, variantId = variant, locationId = "27", menuId = order,
        modifierSelections = listOf(OrderModifierSelection(order, variant, 2)))
    private fun visit() = TableVisit(client, 1, apiBacked = true, locationId = "27", remoteTableId = "54",
        tableCode = "3", openedBy = worker, openedAt = Instant.EPOCH, draft = listOf(line()))

    @Test fun unsentDraftHasNoOfficialNumberAndSurvivesRemovingItsLastProduct() {
        val created = TableService().createOrder(worker, Instant.EPOCH, apiBacked = true)
        val id = created.visits.single().id
        val service = created.add(id, line(), Instant.EPOCH, worker)
        assertNull(service.visits.single().orderNumber)
        assertTrue(service.registeredOrders().single().localDraft)
        val empty = service.adjustDraft(id, line(), -3)
        assertFalse(empty.visits.single().closed)
        assertTrue(empty.registeredOrders().isEmpty())
    }

    @Test fun selectedUnitsOnlyAreCapturedAndIdsPersistAcrossProcessRetry() {
        val pending = captureOrderSend(visit(), mapOf(0 to 2))
        val body = JSONObject(pending.body)
        assertEquals(client, body.getString("client_order_id"))
        assertEquals("54", body.getString("table_id"))
        val payloadLine = body.getJSONObject("round").getJSONArray("lines").getJSONObject(0)
        assertEquals(2, payloadLine.getInt("quantity"))
        assertEquals(1650, payloadLine.getInt("expected_unit_total_minor"))
        assertEquals(2, payloadLine.getJSONArray("selections").getJSONObject(0).getJSONArray("options").getJSONObject(0).getInt("quantity"))
        val retry = PendingOrderSend.decode(pending.encode())
        assertEquals(pending.roundId, retry.roundId)
        assertEquals(pending.captured, retry.captured)
        assertEquals(body.toString(), JSONObject(retry.body).toString())
    }

    @Test fun subsequentCaptureUsesRoundsRouteAndKeepsOfficialOrderIdentity() {
        val existing = visit().copy(remoteOrderId = order, orderNumber = 12, remoteStatus = "open")
        val first = captureOrderSend(existing, mapOf(0 to 1))
        val second = captureOrderSend(existing, mapOf(0 to 1))
        assertEquals("ops/locations/27/orders/$order/rounds", first.path)
        assertFalse(JSONObject(first.body).has("client_order_id"))
        assertNotEquals(first.roundId, second.roundId)
    }

    @Test fun confirmationConsumesOnlyCapturedUnitsAndKeepsRemainingDraft() {
        val original = visit()
        val pending = captureOrderSend(original, mapOf(0 to 2))
        val saved = original.copy(pendingSend = pending.encode())
        val response = parseOpsOrder(fixture(pending.roundId).toString(), "27")
        val ack = TableService(listOf(saved)).acknowledgeSend(client, pending, response, "Local recibido")
        val confirmed = ack.visits.single()
        assertEquals(1, confirmed.draft.single().quantity)
        assertNull(confirmed.pendingSend)
        assertEquals(order, confirmed.remoteOrderId)
        assertEquals(12, confirmed.orderNumber)
        assertEquals("Local recibido", confirmed.locationName)
        assertEquals("Persona del servidor", confirmed.openedBy?.name)
        assertEquals(3300L, confirmed.totalInCents)
    }

    @Test fun detailPreservesUnitIdsAndSeparatesMixedStatesWithoutMarkingEverythingReady() {
        val detail = fixture(round)
        detail.getJSONObject("data").getJSONArray("rounds").getJSONObject(0).getJSONArray("lines").getJSONObject(0)
            .getJSONArray("units").getJSONObject(1).put("status", "ready")
        val parsed = parseOpsOrder(detail.toString(), "27")
        val lines = parsed.rounds!!.single().lines
        assertEquals(2, lines.size)
        assertEquals(2, lines.flatMap { it.remoteUnitIds }.distinct().size)
        assertEquals(setOf(KitchenItemStatus.SENT, KitchenItemStatus.READY), lines.map { it.dispatchStatus }.toSet())
        val service = TableService().mergeOrder(parsed, "Local API")
        assertFalse(service.registeredOrders().single().isFullyReady)
    }

    @Test fun listSummaryDoesNotInventProductsAndSameVersionPreservesKnownDetail() {
        val detail = parseOpsOrder(fixture(round).toString(), "27")
        var service = TableService(listOf(visit())).mergeOrder(detail, "Local API")
        val summaryJson = fixture(round).getJSONObject("data").apply { remove("rounds") }
        val summary = parseOpsOrders(JSONObject().put("data", JSONArray().put(summaryJson)).toString(), "27").single()
        assertNull(summary.rounds)
        service = service.mergeOrder(summary, "Local API")
        assertTrue(service.visits.single().remoteDetailLoaded)
        assertEquals(3, service.visits.single().draft.single().quantity)
        val changed = service.mergeOrder(summary.copy(version = 2), "Local API")
        assertFalse(changed.visits.single().remoteDetailLoaded)
        assertTrue(changed.visits.single().dispatches.isEmpty())
        assertTrue(changed.registeredOrders().single().hasDisplayableProducts)
        assertFalse(changed.registeredOrders().single().isFullyReady)
        assertEquals(3, changed.visits.single().draft.single().quantity)
    }

    @Test fun olderResponseCannotOverwriteNewerServerVersionOrDraft() {
        val remote = parseOpsOrder(fixture(round).toString(), "27")
        val current = TableService(listOf(visit())).mergeOrder(remote.copy(version = 5), "Local API")
        assertEquals(current, current.mergeOrder(remote.copy(version = 4, number = 99), "Otro nombre"))
    }

    @Test fun cancellationRetainsSentHistoryAndUnsentDraft() {
        val remote = parseOpsOrder(fixture(round).toString(), "27")
        val service = TableService(listOf(visit())).mergeOrder(remote, "Local API")
        val canceled = service.mergeOrder(remote.copy(status = "canceled", version = 2,
            canceledAt = Instant.parse("2026-11-25T18:00:00Z"), cancelReason = "Cliente desistió"), "Local API")
        assertTrue(canceled.visits.single().closed)
        assertEquals(3, canceled.visits.single().draft.single().quantity)
        assertEquals(1, canceled.visits.single().dispatches.size)
        assertEquals(RegisteredOrderStatus.CANCELED, canceled.registeredOrders().single().status)
        assertEquals("Cliente desistió", canceled.visits.single().remoteCancelReason)
    }

    @Test fun malformedCrossLocationOrFractionalAmountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { parseOpsOrder(fixture(round).toString(), "99") }
        val fractional = fixture(round).apply { getJSONObject("data").put("total_minor", 33.5) }
        assertThrows(ArithmeticException::class.java) { parseOpsOrder(fractional.toString(), "27") }
        assertThrows(IllegalArgumentException::class.java) { captureOrderSend(visit().copy(remoteTableId = null), mapOf(0 to 1)) }
        assertThrows(IllegalArgumentException::class.java) { captureOrderSend(visit(), mapOf(0 to 4)) }
        assertThrows(IllegalArgumentException::class.java) { captureOrderSend(visit(), emptyMap()) }
    }

    @Test fun unconfirmedOrUnrelatedRoundCannotConsumeTheDraft() {
        val saved = visit()
        val pending = captureOrderSend(saved, mapOf(0 to 1))
        val unrelated = parseOpsOrder(fixture(round).toString(), "27")
        assertThrows(IllegalArgumentException::class.java) {
            TableService(listOf(saved)).acknowledgeSend(client, pending, unrelated, "Local API")
        }
        assertEquals(3, saved.draft.single().quantity)
    }

    @Test fun diskLineRoundTripPreservesServerUnitIdentityAndModifierQuantities() {
        val saved = line().copy(remoteLineNumber = 7, remoteUnitIds = listOf(order, round), kitchenStatus = KitchenItemStatus.SENT)
        assertEquals(saved, readLine(JSONObject(saved.toJson().toString())))
    }

    @Test fun remoteReadyStatusWithoutTransitionTimeDoesNotInventNotificationTime() {
        val json = fixture(round)
        val units = json.getJSONObject("data").getJSONArray("rounds").getJSONObject(0)
            .getJSONArray("lines").getJSONObject(0).getJSONArray("units")
        for (index in 0 until units.length()) units.getJSONObject(index).put("status", "ready")
        val service = TableService().mergeOrder(parseOpsOrder(json.toString(), "27"), "Local API")
        assertTrue(service.registeredOrders().single().isFullyReady)
        val notices = service.readyNoticesFor(worker.id)
        assertEquals(2, notices.size)
        assertTrue(notices.all { it.readyAt == null })
        assertEquals(2, notices.map { it.identity }.distinct().size)
    }

    private fun fixture(clientRound: String): JSONObject {
        val units = JSONArray().put(JSONObject().put("id", "019badb1-ace0-7c10-834a-53c11ab40031").put("status", "sent"))
            .put(JSONObject().put("id", "019badb1-ace0-7c10-834a-53c11ab40032").put("status", "sent"))
        val line = JSONObject().put("line_number", 1).put("product_id", variant).put("variant_id", variant)
            .put("product_name", "Café API").put("variant_name", "Grande").put("note", JSONObject.NULL)
            .put("quantity", 2).put("unit_total_minor", 1650).put("modifiers", JSONArray()).put("units", units)
        val roundJson = JSONObject().put("id", round).put("client_round_id", clientRound).put("sequence", 1)
            .put("sent_at", "2026-11-25T17:40:19.955Z").put("note", JSONObject.NULL)
            .put("sent_by", JSONObject().put("employee_id", "42").put("display_name", "Persona del servidor"))
            .put("lines", JSONArray().put(line))
        val data = JSONObject().put("id", order).put("client_order_id", client).put("location_id", "27")
            .put("number", 12).put("business_date", "2026-11-25").put("status", "open")
            .put("table", JSONObject().put("id", "54").put("code", "3").put("name", "3"))
            .put("currency_code", "PEN").put("total_minor", 3300).put("units_count", 2)
            .put("opened_at", "2026-11-25T17:40:19.955Z")
            .put("opened_by", JSONObject().put("employee_id", "42").put("display_name", "Persona del servidor"))
            .put("version", 1).put("rounds", JSONArray().put(roundJson))
        return JSONObject().put("data", data)
    }
}
