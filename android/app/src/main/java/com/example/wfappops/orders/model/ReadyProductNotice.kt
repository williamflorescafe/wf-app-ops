package com.example.wfappops.orders.model

import java.time.Instant

internal data class ReadyProductNotice(
    val key: DispatchLineKey,
    val table: Int,
    val product: String,
    val quantity: Int,
    val readyAt: Instant?,
    val tableLabel: String? = null,
    val orderNumber: Int? = null,
    val locationId: String? = null,
    val stableKey: String? = null,
    val groupKey: String? = null,
    val locationName: String? = null
) {
    val identity get() = stableKey ?: "${key.visitId}/${key.dispatchIndex}/${key.lineIndex}"
    val groupIdentity get() = groupKey ?: identity
}

internal fun TableService.readyNoticesFor(employeeId: String): List<ReadyProductNotice> = visits
    .filterNot { it.closed }
    .flatMap { visit ->
        visit.dispatches.flatMapIndexed { dispatchIndex, dispatch ->
            if (dispatch.employeeId != employeeId || dispatch.canceledAt != null) return@flatMapIndexed emptyList()
            dispatch.lines.flatMapIndexed { lineIndex, line ->
                if (line.kitchenStatus != KitchenItemStatus.READY || line.quantity <= 0) return@flatMapIndexed emptyList()
                val notice = ReadyProductNotice(
                    key = DispatchLineKey(visit.id, dispatchIndex, lineIndex),
                    table = visit.table,
                    product = line.product,
                    quantity = line.quantity,
                    // Un null remoto significa hora desconocida; el historial registra cuándo se detectó.
                    readyAt = line.readyAt,
                    tableLabel = visit.tableName?.takeIf { it.isNotBlank() } ?: visit.tableCode,
                    orderNumber = visit.orderNumber, locationId = visit.locationId, locationName = visit.locationName,
                    groupKey = if (visit.apiBacked) "${visit.remoteOrderId}/${dispatch.remoteId}/${line.remoteLineNumber}" else null
                )
                if (visit.apiBacked) line.remoteUnitIds.map { unit ->
                    notice.copy(quantity = 1, stableKey = "unit/$unit")
                } else listOf(notice)
            }
        }
    }
    .sortedBy { it.readyAt ?: Instant.MAX }
