package com.example.wfappops.orders.model

import java.time.Instant

internal data class ReadyProductNotice(
    val key: DispatchLineKey,
    val table: Int,
    val product: String,
    val quantity: Int,
    val readyAt: Instant,
    val tableLabel: String? = null
)

internal fun TableService.readyNoticesFor(employeeId: String): List<ReadyProductNotice> = visits
    .filterNot { it.closed }
    .flatMap { visit ->
        visit.dispatches.flatMapIndexed { dispatchIndex, dispatch ->
            if (dispatch.employeeId != employeeId || dispatch.canceledAt != null) return@flatMapIndexed emptyList()
            dispatch.lines.mapIndexedNotNull { lineIndex, line ->
                if (line.kitchenStatus != KitchenItemStatus.READY) return@mapIndexedNotNull null
                ReadyProductNotice(
                    key = DispatchLineKey(visit.id, dispatchIndex, lineIndex),
                    table = visit.table,
                    product = line.product,
                    quantity = line.quantity,
                    readyAt = line.readyAt ?: dispatch.sentAt,
                    tableLabel = visit.tableCode ?: visit.tableName
                )
            }
        }
    }
    .sortedBy { it.readyAt }
