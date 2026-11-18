package com.example.wfappops.orders.model

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
    val kitchenStatus: KitchenItemStatus? = null,
    val canceledAt: Instant? = null,
    val canceledBy: TableWorker? = null,
    val preparationStartedAt: Instant? = null,
    val readyAt: Instant? = null,
    val deliveredAt: Instant? = null
) {
    val totalInCents get() = quantity * unitPriceInCents
    val isCanceled get() = kitchenStatus == KitchenItemStatus.CANCELED
    val canCancel get() = dispatchStatus == KitchenItemStatus.SENT
    val dispatchStatus get() = kitchenStatus ?: KitchenItemStatus.SENT
    val lastStatusAt get() = listOfNotNull(preparationStartedAt, readyAt, deliveredAt, canceledAt).maxOrNull()

    fun asSent() = if (kitchenStatus == null) copy(kitchenStatus = KitchenItemStatus.SENT) else this
}

internal enum class KitchenItemStatus(val wireValue: String) {
    SENT("sent"), IN_PREPARATION("in_preparation"), READY("ready"), DELIVERED("delivered"), CANCELED("canceled")
}

internal data class OrderDispatch(
    val sentAt: Instant,
    val employeeName: String,
    val lines: List<OrderLine>,
    val employeeId: String? = null,
    val legacyNumber: Int? = null,
    val canceledAt: Instant? = null,
    val canceledBy: TableWorker? = null,
    val receivedAt: Instant? = null
) {
    val activeLines get() = if (canceledAt != null) emptyList() else lines.filterNot { it.isCanceled }
    val isCanceled get() = canceledAt != null || (lines.isNotEmpty() && lines.all { it.isCanceled })
    val canCancelLines get() = canceledAt == null && lines.any { it.canCancel }
    val canDeliver get() = canceledAt == null && lines.any { it.kitchenStatus == KitchenItemStatus.READY } &&
        lines.all { it.isCanceled || it.kitchenStatus == KitchenItemStatus.READY || it.kitchenStatus == KitchenItemStatus.DELIVERED }
    val isDelivered get() = canceledAt == null && lines.any { it.kitchenStatus == KitchenItemStatus.DELIVERED } &&
        lines.all { it.isCanceled || it.kitchenStatus == KitchenItemStatus.DELIVERED }
    val lastActivityAt get() = maxOf(
        sentAt, receivedAt ?: sentAt, canceledAt ?: sentAt,
        lines.mapNotNull { it.lastStatusAt }.maxOrNull() ?: sentAt
    )
}

internal data class CashierHandoff(val sentAt: Instant, val sentBy: TableWorker)

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
    val orderStartedAt: Instant? = null,
    val comment: String? = null,
    val cashierHandoff: CashierHandoff? = null
) {
    val totalInCents get() = dispatches.sumOf { dispatch ->
        dispatch.activeLines.sumOf { it.totalInCents }
    }
    val canSendToCashier get() = !closed && orderNumber != null && dispatches.isNotEmpty() && draft.isEmpty() &&
        dispatches.all { dispatch -> dispatch.activeLines.all { it.dispatchStatus == KitchenItemStatus.DELIVERED } }
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
        val lastDispatch = visit.dispatches.maxOf { it.lastActivityAt }
        val activeLines = visit.dispatches.flatMap { it.activeLines }
        val ready = activeLines.sumOf { if (it.kitchenStatus == KitchenItemStatus.READY) it.quantity.toLong() else 0L }
        val status = when {
            visit.closed -> RegisteredOrderStatus.CLOSED
            ready > 0 -> RegisteredOrderStatus.READY
            activeLines.any { it.kitchenStatus == KitchenItemStatus.IN_PREPARATION } -> RegisteredOrderStatus.PREPARING
            visit.draft.isNotEmpty() -> RegisteredOrderStatus.DRAFT
            activeLines.any { it.dispatchStatus == KitchenItemStatus.SENT } -> RegisteredOrderStatus.SENT
            activeLines.isNotEmpty() && activeLines.all { it.kitchenStatus == KitchenItemStatus.DELIVERED } ->
                RegisteredOrderStatus.DELIVERED
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

    fun saveComment(visitId: String, comment: String) = changeVisit(visitId) { visit ->
        visit.copy(comment = comment.trim().ifEmpty { null })
    }

    fun sendToCashier(visitId: String, worker: TableWorker, at: Instant) = changeVisit(visitId) { visit ->
        if (!visit.canSendToCashier) return@changeVisit visit
        val sentAt = maxOf(at, visit.dispatches.maxOf { it.lastActivityAt })
        visit.copy(closed = true, cashierHandoff = CashierHandoff(sentAt, worker))
    }

    fun sendLocally(
        visitId: String,
        worker: TableWorker,
        sentAt: Instant,
        receivedAt: Instant = sentAt
    ) = changeVisit(visitId) { visit ->
        if (visit.draft.isEmpty()) return@changeVisit visit
        val dispatch = OrderDispatch(
            sentAt, worker.name, visit.draft.map(OrderLine::asSent), worker.id,
            receivedAt = maxOf(sentAt, receivedAt)
        )
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

    fun advanceKitchenLine(key: DispatchLineKey, expected: KitchenItemStatus, at: Instant) =
        changeLine(key, expected, at) { line, changedAt ->
            when (line.dispatchStatus) {
                KitchenItemStatus.SENT -> line.copy(kitchenStatus = KitchenItemStatus.IN_PREPARATION, preparationStartedAt = changedAt)
                KitchenItemStatus.IN_PREPARATION -> line.copy(kitchenStatus = KitchenItemStatus.READY, readyAt = changedAt)
                else -> line
            }
        }

    fun deliverLine(key: DispatchLineKey, at: Instant) =
        changeLine(key, KitchenItemStatus.READY, at) { line, changedAt ->
            line.delivered(changedAt)
        }

    fun deliverDispatch(visitId: String, dispatchIndex: Int, at: Instant) =
        changeDispatch(visitId, dispatchIndex) { dispatch ->
            if (!dispatch.canDeliver) return@changeDispatch dispatch
            dispatch.copy(lines = dispatch.lines.map { line ->
                if (line.kitchenStatus == KitchenItemStatus.READY) line.delivered(dispatch.statusTime(line, at))
                else line
            })
        }

    private fun changeLine(
        key: DispatchLineKey,
        expected: KitchenItemStatus,
        at: Instant,
        change: (OrderLine, Instant) -> OrderLine
    ) = changeDispatch(key.visitId, key.dispatchIndex) { dispatch ->
        val line = dispatch.lines.getOrNull(key.lineIndex) ?: return@changeDispatch dispatch
        if (line.dispatchStatus != expected) return@changeDispatch dispatch
        val changedAt = dispatch.statusTime(line, at)
        val updated = change(line, changedAt)
        dispatch.copy(lines = dispatch.lines.toMutableList().apply { set(key.lineIndex, updated) })
    }

    fun cancelLocalDispatch(visitId: String, index: Int, worker: TableWorker, canceledAt: Instant) =
        changeDispatch(visitId, index) { dispatch ->
            if (dispatch.canCancelLines && dispatch.activeLines.all { it.canCancel }) {
                dispatch.copy(canceledAt = canceledAt, canceledBy = worker)
            } else dispatch
        }

    fun cancelLocalLines(
        visitId: String,
        dispatchIndex: Int,
        lineIndices: Set<Int>,
        worker: TableWorker,
        canceledAt: Instant
    ) = changeDispatch(visitId, dispatchIndex) { dispatch ->
        require(lineIndices.all { it in dispatch.lines.indices })
        dispatch.copy(lines = dispatch.lines.mapIndexed { index, line ->
            if (index in lineIndices && line.canCancel) {
                line.copy(
                    kitchenStatus = KitchenItemStatus.CANCELED,
                    canceledAt = dispatch.statusTime(line, canceledAt),
                    canceledBy = worker
                )
            } else line
        })
    }

    private fun changeDispatch(visitId: String, index: Int, change: (OrderDispatch) -> OrderDispatch) =
        changeVisit(visitId) { visit ->
        val dispatch = visit.dispatches.getOrNull(index)?.takeUnless { it.canceledAt != null } ?: return@changeVisit visit
        val dispatches = visit.dispatches.toMutableList()
        dispatches[index] = change(dispatch)
        visit.copy(dispatches = dispatches)
    }

    private fun changeVisit(id: String, change: (TableVisit) -> TableVisit): TableService = copy(
        visits = visits.map { if (it.id == id && !it.closed) change(it) else it }
    )
}

private fun OrderDispatch.statusTime(line: OrderLine, at: Instant) =
    maxOf(at, receivedAt ?: sentAt, line.lastStatusAt ?: sentAt)

private fun OrderLine.delivered(at: Instant) =
    copy(kitchenStatus = KitchenItemStatus.DELIVERED, deliveredAt = at)

internal data class RegisteredOrder(
    val visitId: String,
    val table: Int,
    val number: Int,
    val lastActivityAt: Instant,
    val status: RegisteredOrderStatus,
    val readyQuantity: Long
)

internal enum class RegisteredOrderStatus { OPEN, DRAFT, SENT, PREPARING, READY, DELIVERED, CLOSED }

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
