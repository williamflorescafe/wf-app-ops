package com.example.wfappops.orders.model

import java.time.Instant
import org.json.JSONObject

/** Simulación de pruebas separada de las líneas originales del servidor y vinculada a su versión. */
internal data class LocalKitchenStatus(
    val version: Int?,
    val serverStatus: KitchenItemStatus,
    val status: KitchenItemStatus,
    val preparationStartedAt: Instant?,
    val readyAt: Instant?,
    val deliveredAt: Instant?
) {
    fun encode(): JSONObject = JSONObject().put("version", version ?: JSONObject.NULL)
        .put("serverStatus", serverStatus.name).put("status", status.name)
        .put("preparing", preparationStartedAt?.toEpochMilli() ?: JSONObject.NULL)
        .put("ready", readyAt?.toEpochMilli() ?: JSONObject.NULL)
        .put("served", deliveredAt?.toEpochMilli() ?: JSONObject.NULL)

    companion object {
        fun decode(json: JSONObject) = LocalKitchenStatus(
            if (json.isNull("version")) null else json.getInt("version"),
            KitchenItemStatus.valueOf(json.getString("serverStatus")), KitchenItemStatus.valueOf(json.getString("status")),
            json.instant("preparing"), json.instant("ready"), json.instant("served"))
        private fun JSONObject.instant(key: String) = if (isNull(key)) null else Instant.ofEpochMilli(getLong(key))
    }
}

private fun OrderLine.localStatusKey(dispatch: Int, line: Int) = if (remoteUnitIds.isEmpty()) "local/$dispatch/$line"
    else "units/${remoteUnitIds.sorted().joinToString("/")}"

internal fun TableService.withLocalKitchenStatuses() = copy(visits = visits.map { visit ->
    if (visit.closed || visit.localKitchenStatuses.isEmpty()) visit else visit.copy(
        dispatches = visit.dispatches.mapIndexed { dispatchIndex, dispatch ->
            if (dispatch.canceledAt != null) dispatch else dispatch.copy(lines = dispatch.lines.mapIndexed { index, line ->
                val saved = visit.localKitchenStatuses[line.localStatusKey(dispatchIndex, index)]
                if (saved == null || saved.version != visit.remoteVersion || saved.serverStatus != line.dispatchStatus) line
                else line.copy(kitchenStatus = saved.status, preparationStartedAt = saved.preparationStartedAt,
                    readyAt = saved.readyAt, deliveredAt = saved.deliveredAt)
            })
        }
    )
})

/** No altera dispatches, precios, IDs, número oficial, versión ni ocupación remota. */
internal fun TableService.advanceLocalKitchenStatus(key: DispatchLineKey, expectedLine: OrderLine,
    expectedVersion: Int?, at: Instant): TableService {
    val raw = visits.first { it.id == key.visitId }
    check(!raw.closed && raw.remoteVersion == expectedVersion) { "La comanda cambió. Actualízala antes de cambiar el estado." }
    val projected = withLocalKitchenStatuses()
    val dispatch = checkNotNull(projected.visits.first { it.id == key.visitId }.dispatches.getOrNull(key.dispatchIndex))
    val current = checkNotNull(dispatch.lines.getOrNull(key.lineIndex))
    check(current == expectedLine && dispatch.canceledAt == null && current.quantity > 0) { "El producto cambió. Revisa su estado actual." }
    check(current.dispatchStatus in setOf(KitchenItemStatus.SENT, KitchenItemStatus.IN_PREPARATION, KitchenItemStatus.READY))
    // TableStore persiste milisegundos: la proyección que vuelve a la UI debe usar la misma precisión.
    val changedAt = Instant.ofEpochMilli(at.toEpochMilli())
    val next = projected.advanceOrderLine(key, current.dispatchStatus, changedAt).visits.first { it.id == key.visitId }
        .dispatches[key.dispatchIndex].lines[key.lineIndex]
    val original = raw.dispatches[key.dispatchIndex].lines[key.lineIndex]
    val state = LocalKitchenStatus(raw.remoteVersion, original.dispatchStatus, next.dispatchStatus,
        next.preparationStartedAt.cacheTime(), next.readyAt.cacheTime(), next.deliveredAt.cacheTime())
    return copy(visits = visits.map { visit -> if (visit.id != raw.id) visit else visit.copy(
        localKitchenStatuses = visit.localKitchenStatuses.filterValues { it.version == raw.remoteVersion } +
            (original.localStatusKey(key.dispatchIndex, key.lineIndex) to state)) })
}

private fun Instant?.cacheTime() = this?.let { Instant.ofEpochMilli(it.toEpochMilli()) }
