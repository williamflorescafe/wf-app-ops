package com.example.wfappops.orders.model

import java.time.Instant

internal data class DispatchLineKey(val visitId: String, val dispatchIndex: Int, val lineIndex: Int)

internal data class KitchenLine(val key: DispatchLineKey, val line: OrderLine)

internal data class KitchenDispatch(
    val visitId: String,
    val table: Int,
    val orderNumber: Int?,
    val ordinal: Int,
    val sentAt: Instant,
    val lines: List<KitchenLine>,
    val receivedAt: Instant?,
    val employeeId: String?
)

internal data class KitchenQueue(
    val inProgress: List<KitchenDispatch>,
    val confirmed: List<KitchenDispatch>
) {
    fun forEmployee(employeeId: String) = KitchenQueue(
        inProgress.filter { it.employeeId == employeeId },
        confirmed.filter { it.employeeId == employeeId }
    )
}

internal fun TableService.kitchenQueue(): KitchenQueue {
    val dispatches = visits.filterNot { it.closed }.flatMap { visit ->
        visit.dispatches.mapIndexedNotNull { index, dispatch ->
            if (dispatch.canceledAt != null) return@mapIndexedNotNull null
            val lines = dispatch.lines.mapIndexedNotNull { lineIndex, line ->
                when (line.dispatchStatus) {
                    KitchenItemStatus.SENT, KitchenItemStatus.IN_PREPARATION, KitchenItemStatus.READY ->
                        KitchenLine(DispatchLineKey(visit.id, index, lineIndex), line)
                    else -> null
                }
            }
            if (lines.isEmpty()) return@mapIndexedNotNull null
            KitchenDispatch(
                visit.id, visit.table, visit.orderNumber, index + 1,
                dispatch.sentAt, lines, dispatch.receivedAt, dispatch.employeeId
            )
        }
    }.sortedWith(compareBy<KitchenDispatch> { it.sentAt }.thenBy { it.orderNumber }.thenBy { it.ordinal })

    val inProgress = mutableListOf<KitchenDispatch>()
    val confirmed = mutableListOf<KitchenDispatch>()
    for (dispatch in dispatches) {
        val (ready, pending) = dispatch.lines.partition { it.line.dispatchStatus == KitchenItemStatus.READY }
        if (pending.isNotEmpty()) inProgress.add(dispatch.copy(lines = pending))
        if (ready.isNotEmpty()) confirmed.add(dispatch.copy(lines = ready))
    }
    return KitchenQueue(inProgress, confirmed)
}
