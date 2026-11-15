package com.example.wfappops.tables

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant

/** Guarda los cambios de cada atención en una sola escritura. */
internal class TableStore(context: Context, businessId: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "ops-mesas-${Uri.encode(businessId)}.json"))

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
                    dispatch.getJSONArray("lines").readItems(::readLine),
                    dispatch.optString("employeeId").ifBlank { null },
                    dispatch.optionalInt("legacyNumber") ?: dispatch.optionalInt("number"),
                    canceledAt = if (dispatch.isNull("canceledAt")) null else Instant.ofEpochMilli(dispatch.getLong("canceledAt")),
                    canceledBy = dispatch.optJSONObject("canceledBy")?.toWorker()
                )
            }.let { if (savedDispatches == null) it.sortedBy { dispatch -> dispatch.sentAt } else it }
            TableVisit(
                id = visit.getString("id"),
                table = visit.getInt("table"),
                closed = visit.getBoolean("closed"),
                draft = visit.getJSONArray("draft").readItems(::readLine),
                dispatches = dispatches,
                openedBy = visit.optJSONObject("openedBy")?.toWorker(),
                responsible = visit.optJSONObject("responsible")?.toWorker(),
                openedAt = if (visit.isNull("openedAt")) null else Instant.ofEpochMilli(visit.getLong("openedAt")),
                orderNumber = visit.optionalInt("orderNumber") ?: dispatches.firstOrNull()?.legacyNumber,
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
    }
}

private fun TableVisit.toJson() = JSONObject()
    .put("id", id)
    .put("table", table)
    .put("closed", closed)
    .put("openedAt", openedAt?.toEpochMilli())
    .put("orderNumber", orderNumber)
    .put("orderStartedAt", orderStartedAt?.toEpochMilli())
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

internal fun readLine(json: JSONObject): OrderLine {
    val options = json.getJSONArray("options")
    return OrderLine(
        json.getString("product"), json.getInt("quantity"), json.getLong("price"),
        List(options.length()) { options.getString(it) },
        json.optString("note").takeUnless { json.isNull("note") || it.isBlank() },
        KitchenItemStatus.entries.firstOrNull { it.wireValue == json.optString("kitchenStatus") }
    )
}

private fun JSONObject.optionalInt(key: String): Int? = if (isNull(key)) null else getInt(key)

private fun <T> JSONArray.readItems(read: (JSONObject) -> T): List<T> = List(length()) { read(getJSONObject(it)) }
