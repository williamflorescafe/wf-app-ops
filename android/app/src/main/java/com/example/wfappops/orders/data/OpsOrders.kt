package com.example.wfappops.orders.data

import com.example.wfappops.orders.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** El resumen nunca inventa productos ni estados de cocina que no entrega el servidor. */
internal data class OpsOrder(
    val id: String, val clientOrderId: String, val locationId: String, val number: Int,
    val businessDate: String, val status: String, val tableId: String, val tableCode: String,
    val tableName: String, val currency: String, val totalMinor: Long, val unitsCount: Long,
    val openedAt: Instant, val openedBy: TableWorker, val version: Int,
    val rounds: List<OrderDispatch>?, val canceledAt: Instant? = null, val cancelReason: String? = null
)

internal fun parseOpsOrders(payload: String, locationId: String): List<OpsOrder> =
    JSONObject(payload).getJSONArray("data").objects().map { parseOpsOrder(it, locationId, false) }.also { orders ->
        require(orders.map { it.id }.distinct().size == orders.size)
        require(orders.map { it.tableId }.distinct().size == orders.size)
        require(orders.all { it.status == "open" })
    }

internal fun parseOpsOrder(payload: String, locationId: String): OpsOrder =
    parseOpsOrder(JSONObject(payload).getJSONObject("data"), locationId, true)

private fun parseOpsOrder(json: JSONObject, expectedLocation: String, detail: Boolean): OpsOrder {
    val location = json.getString("location_id").also { require(it == expectedLocation) }
    val currency = json.getString("currency_code").also { java.util.Currency.getInstance(it) }
    val table = json.getJSONObject("table")
    val orderId = json.uuid("id")
    val rounds = if (!detail) null else json.getJSONArray("rounds").objects().map { round ->
        val sentAt = Instant.parse(round.getString("sent_at"))
        val sender = round.getJSONObject("sent_by").worker()
        OrderDispatch(sentAt, sender.name, round.getJSONArray("lines").objects().flatMap { line ->
            val units = line.getJSONArray("units").objects()
            require(units.size == line.positiveInt("quantity"))
            require(units.map { it.uuid("id") }.distinct().size == units.size)
            units.groupBy { unit ->
                KitchenItemStatus.entries.firstOrNull { it.wireValue == unit.getString("status") }
                    ?: error("Unsupported kitchen status")
            }.map { (status, group) ->
                val modifiers = line.getJSONArray("modifiers").objects()
                OrderLine(
                    product = line.getString("product_name"), quantity = group.size,
                    unitPriceInCents = line.exactLong("unit_total_minor").also { require(it >= 0) },
                    options = listOfNotNull(line.nullableText("variant_name")) + modifiers.map {
                        it.getString("option_name") + if (it.positiveInt("quantity") > 1) " ×${it.positiveInt("quantity")}" else ""
                    },
                    note = line.nullableText("note"), kitchenStatus = status,
                    productId = line.uuid("product_id"), variantId = line.uuid("variant_id"),
                    modifierSelections = modifiers.map { OrderModifierSelection(it.uuid("modifier_group_id"), it.uuid("option_id"), it.positiveInt("quantity")) },
                    currencyCode = currency, locationId = location,
                    remoteUnitIds = group.map { it.uuid("id") }, remoteLineNumber = line.positiveInt("line_number")
                )
            }
        }, employeeId = sender.id, remoteId = round.uuid("id"), sequence = round.positiveInt("sequence"),
            clientRoundId = round.uuid("client_round_id"), note = round.nullableText("note"))
    }.sortedBy { it.sequence }.also { rounds ->
        require(rounds.map { it.remoteId }.distinct().size == rounds.size)
        require(rounds.map { it.sequence }.distinct().size == rounds.size)
        val units = rounds.flatMap { it.lines }.flatMap { it.remoteUnitIds }
        require(units.distinct().size == units.size)
    }
    return OpsOrder(orderId, json.uuid("client_order_id"), location, json.positiveInt("number"),
        json.getString("business_date").also { LocalDate.parse(it) },
        json.getString("status"), table.getString("id").also { require(Regex("[1-9][0-9]*").matches(it)) },
        table.getString("code"), table.getString("name"), currency,
        json.exactLong("total_minor").also { require(it >= 0) }, json.exactLong("units_count").also { require(it >= 0) },
        Instant.parse(json.getString("opened_at")), json.getJSONObject("opened_by").worker(), json.positiveInt("version"),
        rounds, json.nullableText("canceled_at")?.let(Instant::parse), json.nullableText("cancel_reason"))
}

internal fun TableService.mergeOrder(order: OpsOrder, locationName: String): TableService {
    val existing = visits.firstOrNull { it.remoteOrderId == order.id && it.locationId == order.locationId }
        ?: visits.firstOrNull { it.id == order.clientOrderId && it.remoteOrderId == null && it.locationId == order.locationId }
    if (existing?.remoteVersion != null && existing.remoteVersion > order.version) return this
    val keepDetail = existing?.remoteVersion == order.version && existing.remoteDetailLoaded
    val visit = (existing ?: TableVisit(UUID.randomUUID().toString(), Math.addExact(visits.maxOfOrNull { it.table } ?: 0, 1))).copy(
        apiBacked = true, remoteOrderId = order.id, remoteVersion = order.version, remoteStatus = if (order.canceledAt != null) "canceled" else order.status,
        remoteCanceledAt = order.canceledAt, remoteCancelReason = order.cancelReason,
        remoteTotalMinor = order.totalMinor, remoteUnitsCount = order.unitsCount,
        remoteDetailLoaded = order.rounds != null || keepDetail, businessDate = order.businessDate,
        localKitchenStatuses = existing?.localKitchenStatuses.orEmpty().filterValues { it.version == order.version },
        orderNumber = order.number, orderStartedAt = order.openedAt, openedAt = order.openedAt,
        openedBy = order.openedBy, responsible = order.openedBy, numberReserved = false,
        closed = order.status in setOf("closed", "canceled", "cancelled") || order.canceledAt != null,
        locationId = order.locationId, locationName = locationName,
        remoteTableId = order.tableId, tableCode = order.tableCode, tableName = order.tableName,
        dispatches = order.rounds ?: existing?.dispatches.orEmpty().takeIf { keepDetail }.orEmpty()
    )
    return copy(visits = if (existing == null) visits + visit else visits.map { if (it.id == visit.id) visit else it })
}

/** Captura durable e inmutable: se reutiliza exactamente al reintentar, incluso tras reiniciar. */
internal data class PendingOrderSend(val path: String, val body: String, val roundId: String, val captured: List<OrderLine>) {
    fun encode(): String = JSONObject().put("path", path).put("body", JSONObject(body)).put("roundId", roundId)
        .put("captured", JSONArray(captured.map { it.toJson() })).toString()
    companion object {
        fun decode(value: String): PendingOrderSend {
            val json = JSONObject(value)
            return PendingOrderSend(json.getString("path"), json.getJSONObject("body").toString(), json.uuid("roundId"),
                json.getJSONArray("captured").objects().map(::readLine))
        }
    }
}

internal fun captureOrderSend(visit: TableVisit, quantities: Map<Int, Int>): PendingOrderSend {
    require(!visit.closed && visit.pendingSend == null)
    require(visit.remoteOrderId == null || visit.remoteStatus == "open") { "Actualiza la comanda antes de enviar nuevos productos." }
    val location = requireNotNull(visit.locationId) { "Selecciona un local antes de enviar." }.also { require(Regex("[1-9][0-9]*").matches(it)) }
    val table = requireNotNull(visit.remoteTableId) { "Selecciona una mesa antes de enviar." }.also { require(Regex("[1-9][0-9]*").matches(it)) }
    val captured = quantities.toSortedMap().map { (index, quantity) ->
        val line = visit.draft[index]
        require(quantity in 1..line.quantity && line.locationId == location && line.variantId != null) {
            "Revisa los productos del borrador y selecciónalos desde la carta del local actual."
        }
        require(line.note == null || line.note.length <= 255) { "La nota de un producto no puede superar 255 caracteres." }
        line.copy(quantity = quantity)
    }
    require(captured.size in 1..60 && captured.map { it.currencyCode }.distinct().size == 1)
    val roundId = UUID.randomUUID().toString()
    val round = JSONObject().put("client_round_id", roundId).put("note", visit.comment ?: JSONObject.NULL)
        .put("lines", JSONArray(captured.map { line ->
            JSONObject().put("variant_id", line.variantId).put("quantity", line.quantity)
                .put("note", line.note ?: JSONObject.NULL).put("expected_unit_total_minor", line.unitPriceInCents)
                .put("selections", JSONArray(line.modifierSelections.groupBy { it.groupId }.map { (groupId, options) ->
                    JSONObject().put("modifier_group_id", groupId).put("options", JSONArray(options.map {
                        JSONObject().put("option_id", it.optionId).put("quantity", it.quantity)
                    }))
                }))
        }))
    require(visit.comment == null || visit.comment.length <= 255) { "El comentario del envío no puede superar 255 caracteres." }
    val path = "ops/locations/$location/orders" + (visit.remoteOrderId?.let { "/$it/rounds" } ?: "")
    val body = if (visit.remoteOrderId != null) round else JSONObject().put("client_order_id", visit.id)
        .put("table_id", table).put("guests_count", JSONObject.NULL).put("round", round)
    return PendingOrderSend(path, body.toString(), roundId, captured)
}

internal fun TableService.acknowledgeSend(visitId: String, pending: PendingOrderSend, order: OpsOrder, locationName: String): TableService {
    require(order.rounds?.any { it.clientRoundId == pending.roundId } == true)
    val original = visits.first { it.id == visitId }
    require(original.remoteOrderId?.let { it == order.id } ?: (order.clientOrderId == original.id))
    val merged = mergeOrder(order, locationName)
    return merged.copy(visits = merged.visits.map { visit ->
        if (visit.id != visitId) visit else visit.copy(
            draft = pending.captured.fold(visit.draft) { lines, captured -> lines.adjustCartLine(captured, -captured.quantity) }, pendingSend = null, comment = null
        )
    })
}

private fun JSONObject.worker() = TableWorker(getString("employee_id"), getString("display_name"))
private fun JSONObject.exactLong(key: String) = BigDecimal(get(key).toString()).longValueExact()
private fun JSONObject.positiveInt(key: String) = Math.toIntExact(exactLong(key)).also { require(it > 0) }
private fun JSONObject.uuid(key: String) = getString(key).also { require(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(it)); UUID.fromString(it) }
private fun JSONObject.nullableText(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
