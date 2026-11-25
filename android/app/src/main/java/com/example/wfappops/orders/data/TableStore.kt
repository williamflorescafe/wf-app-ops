package com.example.wfappops.orders.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.example.wfappops.orders.model.CashierHandoff
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderDispatch
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.ResponsibilityTransfer
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.model.TableWorker
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Guarda los cambios de cada atención en una sola escritura. */
internal class TableStore(context: Context, businessId: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "ops-mesas-${Uri.encode(businessId)}.json"))
    private val revision = synchronized(lock) {
        revisions.getOrPut(file.baseFile.absolutePath) { MutableStateFlow(0L) }
    }
    val changes = revision.asStateFlow()

    fun read(): TableService = synchronized(lock) { readLocked() }

    fun update(transform: (TableService) -> TableService): TableService = synchronized(lock) {
        val current = readLocked()
        val updated = transform(current)
        if (updated != current) {
            val bytes = JSONArray(updated.visits.map { it.toJson() }).toString().encodeToByteArray()
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (error: IOException) {
                file.failWrite(stream)
                throw error
            }
            if (!file.readFully().contentEquals(bytes)) throw IOException("Could not persist table changes")
            revision.value++
        }
        updated
    }

    private fun readLocked(): TableService {
        val bytes = try {
            file.readFully()
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error
            return TableService()
        }
        val visits = JSONArray(bytes.decodeToString()).readItems { visit ->
            val savedDispatches = visit.optJSONArray("dispatches")
            val dispatches = (savedDispatches ?: visit.getJSONArray("tickets")).readItems { dispatch ->
                OrderDispatch(
                    Instant.ofEpochMilli(dispatch.getLong("sentAt")),
                    dispatch.getString("employeeName"),
                    dispatch.getJSONArray("lines").readItems { readLine(it).asSent() },
                    dispatch.optString("employeeId").ifBlank { null },
                    dispatch.optionalInt("legacyNumber") ?: dispatch.optionalInt("number"),
                    canceledAt = dispatch.optionalInstant("canceledAt"),
                    canceledBy = dispatch.optJSONObject("canceledBy")?.toWorker(),
                    receivedAt = dispatch.optionalInstant("receivedAt")
                )
            }.let { if (savedDispatches == null) it.sortedBy { dispatch -> dispatch.sentAt } else it }
            TableVisit(
                id = visit.getString("id"),
                table = visit.getInt("table"),
                numberReserved = visit.optBoolean("numberReserved", false),
                remoteTableId = visit.optString("remoteTableId").takeUnless { visit.isNull("remoteTableId") },
                locationId = visit.optString("locationId").takeUnless { visit.isNull("locationId") },
                tableName = visit.optString("tableName").takeUnless { visit.isNull("tableName") },
                tableCode = visit.optString("tableCode").takeUnless { visit.isNull("tableCode") },
                locationName = visit.optString("locationName").takeUnless { visit.isNull("locationName") },
                closed = visit.getBoolean("closed"),
                draft = visit.getJSONArray("draft").readItems(::readLine),
                dispatches = dispatches,
                openedBy = visit.optJSONObject("openedBy")?.toWorker(),
                responsible = visit.optJSONObject("responsible")?.toWorker(),
                openedAt = if (visit.isNull("openedAt")) null else Instant.ofEpochMilli(visit.getLong("openedAt")),
                orderNumber = visit.optionalInt("orderNumber") ?: dispatches.firstOrNull()?.legacyNumber,
                comment = visit.optString("comment", "").takeIf { it.isNotBlank() && !visit.isNull("comment") },
                cashierHandoff = visit.optJSONObject("cashierHandoff")?.let { handoff ->
                    CashierHandoff(Instant.ofEpochMilli(handoff.getLong("sentAt")), handoff.getJSONObject("sentBy").toWorker())
                },
                orderStartedAt = if (visit.isNull("orderStartedAt")) dispatches.firstOrNull()?.sentAt
                    else Instant.ofEpochMilli(visit.getLong("orderStartedAt")),
                transfers = visit.optJSONArray("transfers")?.readItems { transfer ->
                    ResponsibilityTransfer(
                        previous = transfer.optJSONObject("previous")?.toWorker(),
                        next = transfer.getJSONObject("next").toWorker(),
                        changedBy = transfer.getJSONObject("changedBy").toWorker(),
                        changedAt = Instant.ofEpochMilli(transfer.getLong("changedAt"))
                    )
                }.orEmpty()
            )
        }
        return TableService(visits)
    }

    private companion object {
        val lock = Any()
        val revisions = mutableMapOf<String, MutableStateFlow<Long>>()
    }
}

private fun TableVisit.toJson() = JSONObject()
    .put("id", id)
    .put("table", table)
    .put("numberReserved", numberReserved)
    .put("remoteTableId", remoteTableId)
    .put("locationId", locationId)
    .put("tableName", tableName)
    .put("tableCode", tableCode)
    .put("locationName", locationName)
    .put("closed", closed)
    .put("openedAt", openedAt?.toEpochMilli())
    .put("orderNumber", orderNumber)
    .put("orderStartedAt", orderStartedAt?.toEpochMilli())
    .put("comment", comment)
    .put("cashierHandoff", cashierHandoff?.let { handoff ->
        JSONObject().put("sentAt", handoff.sentAt.toEpochMilli()).put("sentBy", handoff.sentBy.toJson())
    })
    .put("openedBy", openedBy?.toJson())
    .put("responsible", responsible?.toJson())
    .put("transfers", JSONArray(transfers.map { transfer ->
        JSONObject()
            .put("previous", transfer.previous?.toJson())
            .put("next", transfer.next.toJson())
            .put("changedBy", transfer.changedBy.toJson())
            .put("changedAt", transfer.changedAt.toEpochMilli())
    }))
    .put("draft", JSONArray(draft.map { it.toJson() }))
    .put("dispatches", JSONArray(dispatches.map { dispatch ->
        JSONObject()
            .put("legacyNumber", dispatch.legacyNumber)
            .put("canceledAt", dispatch.canceledAt?.toEpochMilli())
            .put("canceledBy", dispatch.canceledBy?.toJson())
            .put("sentAt", dispatch.sentAt.toEpochMilli())
            .put("receivedAt", dispatch.receivedAt?.toEpochMilli())
            .put("employeeName", dispatch.employeeName)
            .put("employeeId", dispatch.employeeId)
            .put("lines", JSONArray(dispatch.lines.map { it.toJson() }))
    }))

private fun TableWorker.toJson() = JSONObject().put("id", id).put("name", name)

private fun JSONObject.toWorker() = TableWorker(getString("id"), getString("name"))

internal fun OrderLine.toJson() = JSONObject()
    .put("product", product)
    .put("quantity", quantity)
    .put("price", unitPriceInCents)
    .put("options", JSONArray(options))
    .put("note", note)
    .put("kitchenStatus", kitchenStatus?.wireValue)
    .put("canceledAt", canceledAt?.toEpochMilli())
    .put("canceledBy", canceledBy?.toJson())
    .put("preparationStartedAt", preparationStartedAt?.toEpochMilli())
    .put("readyAt", readyAt?.toEpochMilli())
    .put("deliveredAt", deliveredAt?.toEpochMilli())

internal fun readLine(json: JSONObject): OrderLine {
    val options = json.getJSONArray("options")
    return OrderLine(
        json.getString("product"), json.getInt("quantity"), json.getLong("price"),
        List(options.length()) { options.getString(it) },
        json.optString("note").takeUnless { json.isNull("note") || it.isBlank() },
        KitchenItemStatus.entries.firstOrNull { it.wireValue == json.optString("kitchenStatus") },
        canceledAt = json.optionalInstant("canceledAt"),
        canceledBy = json.optJSONObject("canceledBy")?.toWorker(),
        preparationStartedAt = json.optionalInstant("preparationStartedAt"),
        readyAt = json.optionalInstant("readyAt"),
        deliveredAt = json.optionalInstant("deliveredAt")
    )
}

private fun JSONObject.optionalInt(key: String): Int? = if (isNull(key)) null else getInt(key)

private fun JSONObject.optionalInstant(key: String): Instant? =
    if (isNull(key)) null else Instant.ofEpochMilli(getLong(key))

private fun <T> JSONArray.readItems(read: (JSONObject) -> T): List<T> = List(length()) { read(getJSONObject(it)) }
