package com.example.wfappops.tables

import java.time.Instant
import java.util.UUID

internal val serviceTableNumbers = 1..15

internal data class TableWorker(val id: String, val name: String)

internal data class ResponsibilityTransfer(
    val previous: TableWorker?,
    val next: TableWorker,
    val changedBy: TableWorker,
    val changedAt: Instant
)

internal data class OrderLine(
    val product: String,
    val quantity: Int,
    val unitPriceInCents: Long,
    val options: List<String> = emptyList(),
    val note: String? = null,
    val kitchenStatus: KitchenItemStatus? = null
) {
    val totalInCents get() = quantity * unitPriceInCents
}

internal enum class KitchenItemStatus(val wireValue: String) {
    IN_PREPARATION("in_preparation"), READY("ready"), DELIVERED("delivered"), CANCELED("canceled")
}

internal data class OrderDispatch(
    val sentAt: Instant,
    val employeeName: String,
    val lines: List<OrderLine>,
    val employeeId: String? = null,
    val legacyNumber: Int? = null,
    val canceledAt: Instant? = null,
    val canceledBy: TableWorker? = null
)

internal data class TableVisit(
    val id: String,
    val table: Int,
    val closed: Boolean = false,
    val draft: List<OrderLine> = emptyList(),
    val dispatches: List<OrderDispatch> = emptyList(),
    val openedBy: TableWorker? = null,
    val responsible: TableWorker? = null,
    val transfers: List<ResponsibilityTransfer> = emptyList(),
    val openedAt: Instant? = null,
    val orderNumber: Int? = null,
    val orderStartedAt: Instant? = null
) {
    val totalInCents get() = dispatches.filter { it.canceledAt == null }.sumOf { dispatch ->
        dispatch.lines.sumOf { it.totalInCents }
    }
}

internal data class TableService(val visits: List<TableVisit> = emptyList()) {
    private fun nextTicketNumber(): Int {
        val last = visits.maxOfOrNull { visit ->
            maxOf(visit.orderNumber ?: 0, visit.dispatches.maxOfOrNull { it.legacyNumber ?: 0 } ?: 0)
        } ?: 0
        return last + 1
    }

    fun openVisit(table: Int) = visits.lastOrNull { it.table == table && !it.closed }

    fun ordersBy(employeeId: String): List<RegisteredOrder> = visits.mapNotNull { visit ->
        val number = visit.orderNumber ?: return@mapNotNull null
        if (visit.dispatches.none { it.employeeId == employeeId }) return@mapNotNull null
        val lastDispatch = visit.dispatches.maxOf { it.canceledAt ?: it.sentAt }
        val activeDispatches = visit.dispatches.filter { it.canceledAt == null }
        val ready = activeDispatches.sumOf { dispatch ->
            dispatch.lines.sumOf { if (it.kitchenStatus == KitchenItemStatus.READY) it.quantity.toLong() else 0L }
        }
        val status = when {
            visit.closed -> RegisteredOrderStatus.CLOSED
            ready > 0 -> RegisteredOrderStatus.READY
            activeDispatches.any { dispatch ->
                dispatch.lines.any { it.kitchenStatus == KitchenItemStatus.IN_PREPARATION }
            } -> RegisteredOrderStatus.PREPARING
            visit.draft.isNotEmpty() -> RegisteredOrderStatus.DRAFT
            else -> RegisteredOrderStatus.OPEN
        }
        RegisteredOrder(visit.id, visit.table, number, lastDispatch, status, ready)
    }.sortedWith(compareByDescending<RegisteredOrder> { it.lastActivityAt }.thenByDescending { it.number })

    fun open(table: Int, worker: TableWorker, openedAt: Instant = Instant.now()): TableService {
        require(table > 0)
        if (openVisit(table) != null) return this
        val visit = TableVisit(
            UUID.randomUUID().toString(), table, openedBy = worker, responsible = worker, openedAt = openedAt
        )
        return copy(visits = visits + visit)
    }

    fun transfer(visitId: String, next: TableWorker, changedBy: TableWorker, changedAt: Instant) =
        changeVisit(visitId) { visit ->
            if (visit.responsible?.id == next.id) return@changeVisit visit
            val transfer = ResponsibilityTransfer(visit.responsible, next, changedBy, changedAt)
            visit.copy(responsible = next, transfers = visit.transfers + transfer)
        }

    fun add(visitId: String, line: OrderLine) = changeVisit(visitId) { visit ->
        visit.copy(draft = visit.draft.addCartLine(line))
    }

    fun addDraftLines(visitId: String, lines: List<OrderLine>): TableService {
        check(visits.any { it.id == visitId && !it.closed })
        return changeVisit(visitId) { visit ->
            visit.copy(draft = lines.fold(visit.draft) { draft, line -> draft.addCartLine(line) })
        }
    }

    fun adjustDraft(visitId: String, line: OrderLine, delta: Int) = changeVisit(visitId) { visit ->
        visit.copy(draft = visit.draft.adjustCartLine(line, delta))
    }

    fun editDraftNote(visitId: String, line: OrderLine, note: String?) = changeVisit(visitId) { visit ->
        visit.copy(draft = visit.draft.editNote(line, note))
    }

    fun replaceDraft(visitId: String, expected: List<OrderLine>, lines: List<OrderLine>): TableService {
        val visit = checkNotNull(visits.find { it.id == visitId && !it.closed })
        check(visit.draft == expected)
        return changeVisit(visitId) { it.copy(draft = lines) }
    }

    fun sendLocally(visitId: String, worker: TableWorker, sentAt: Instant) = changeVisit(visitId) { visit ->
        if (visit.draft.isEmpty()) return@changeVisit visit
        val dispatch = OrderDispatch(sentAt, worker.name, visit.draft, worker.id)
        visit.copy(
            draft = emptyList(),
            dispatches = visit.dispatches + dispatch,
            orderNumber = visit.orderNumber ?: nextTicketNumber(),
            orderStartedAt = visit.orderStartedAt ?: sentAt
        )
    }

    fun close(visitId: String) = changeVisit(visitId) { visit ->
        if (visit.draft.isEmpty()) visit.copy(closed = true) else visit
    }

    fun cancelLocalDispatch(visitId: String, index: Int, worker: TableWorker, canceledAt: Instant): TableService {
        val visit = checkNotNull(visits.find { it.id == visitId && !it.closed })
        val dispatch = visit.dispatches[index]
        if (dispatch.canceledAt != null) return this
        val dispatches = visit.dispatches.toMutableList()
        dispatches[index] = dispatch.copy(canceledAt = canceledAt, canceledBy = worker)
        return changeVisit(visitId) { it.copy(dispatches = dispatches) }
    }

    private fun changeVisit(id: String, change: (TableVisit) -> TableVisit): TableService = copy(
        visits = visits.map { if (it.id == id && !it.closed) change(it) else it }
    )
}

internal data class RegisteredOrder(
    val visitId: String,
    val table: Int,
    val number: Int,
    val lastActivityAt: Instant,
    val status: RegisteredOrderStatus,
    val readyQuantity: Long
)

internal enum class RegisteredOrderStatus { OPEN, DRAFT, PREPARING, READY, CLOSED }

internal fun List<OrderLine>.addCartLine(line: OrderLine): List<OrderLine> {
    require(line.quantity > 0 && line.unitPriceInCents >= 0)
    return if (none { it.matchesCartLine(line) }) this + line else adjustCartLine(line, line.quantity)
}

internal fun List<OrderLine>.adjustCartLine(line: OrderLine, delta: Int): List<OrderLine> = mapNotNull {
    if (it.matchesCartLine(line)) {
        val quantity = Math.addExact(it.quantity, delta)
        if (quantity > 0) it.copy(quantity = quantity) else null
    } else {
        it
    }
}

private fun OrderLine.matchesCartLine(other: OrderLine) =
    product == other.product && options == other.options && unitPriceInCents == other.unitPriceInCents &&
        note == other.note

internal fun List<OrderLine>.editNote(line: OrderLine, note: String?): List<OrderLine> {
    val current = firstOrNull { it.matchesCartLine(line) } ?: return this
    if (current.note == note) return this
    return filterNot { it.matchesCartLine(current) }.addCartLine(current.copy(note = note))
}
