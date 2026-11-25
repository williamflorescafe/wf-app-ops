package com.example.wfappops.orders.model

import java.time.Instant
import java.util.UUID

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
    val cashierHandoff: CashierHandoff? = null,
    val remoteTableId: String? = null,
    val locationId: String? = null,
    val tableName: String? = null,
    val locationName: String? = null,
    val tableCode: String? = null,
    val numberReserved: Boolean = false
) {
    val isDiscardedDraft get() = closed && orderNumber != null && draft.isEmpty() && dispatches.isEmpty()
    val hasRecordedProducts get() = draft.any { it.quantity > 0 } || dispatches.any { dispatch -> dispatch.lines.any { it.quantity > 0 } }
    val totalInCents get() = dispatches.sumOf { dispatch ->
        dispatch.activeLines.sumOf { it.totalInCents }
    }
    val canSendToCashier get() = !closed && orderNumber != null && dispatches.isNotEmpty() && draft.isEmpty() &&
        dispatches.all { dispatch -> dispatch.activeLines.all { it.dispatchStatus == KitchenItemStatus.DELIVERED } }
}

internal data class TableService(val visits: List<TableVisit> = emptyList()) {
    fun createOrder(worker: TableWorker, at: Instant = Instant.now()): TableService {
        val key = Math.addExact(visits.maxOfOrNull { it.table } ?: 0, 1)
        val opened = open(key, worker, at)
        return opened.reserveOrderNumber(opened.visits.last().id, at)
    }

    fun reserveOrderNumber(visitId: String, at: Instant = Instant.now()) = changeVisit(visitId) { visit ->
        if (visit.orderNumber != null) visit else visit.copy(
            orderNumber = nextTicketNumber(), orderStartedAt = at, numberReserved = true
        )
    }

    fun assignApiTable(visitId: String, location: com.example.wfappops.auth.model.TeamLocation,
                       table: com.example.wfappops.tables.AvailableTable): TableService {
        val others = visits.filter { !it.closed && it.id != visitId && it.locationId == location.id && it.remoteTableId == table.id }
        check(others.none { it.hasRecordedProducts })
        val available = copy(visits = visits.map { if (it in others) it.copy(closed = true) else it })
        return available.changeVisit(visitId) { visit -> visit.copy(locationId = location.id, locationName = location.name,
            remoteTableId = table.id, tableName = table.name, tableCode = table.code) }
    }
    fun openApiTable(
        location: com.example.wfappops.auth.model.TeamLocation,
        table: com.example.wfappops.tables.AvailableTable,
        worker: TableWorker
    ): TableService {
        if (visits.any { !it.closed && it.locationId == location.id && it.remoteTableId == table.id }) {
            return copy(visits = visits.map { visit ->
                if (!visit.closed && visit.locationId == location.id && visit.remoteTableId == table.id)
                    visit.copy(tableCode = table.code, tableName = table.name, locationName = location.name)
                else visit
            })
        }
        // El número interno solo mantiene la compatibilidad del flujo manual; no es el ID remoto.
        val internalNumber = Math.addExact(visits.maxOfOrNull { it.table } ?: 0, 1)
        val opened = open(internalNumber, worker)
        return opened.copy(visits = opened.visits.map { visit ->
            if (visit.table == internalNumber && !visit.closed) visit.copy(
                remoteTableId = table.id, locationId = location.id, tableName = table.name,
                locationName = location.name, tableCode = table.code
            ) else visit
        })
    }
    private fun nextTicketNumber(): Int {
        val last = visits.maxOfOrNull { visit ->
            maxOf(visit.orderNumber ?: 0, visit.dispatches.maxOfOrNull { it.legacyNumber ?: 0 } ?: 0)
        } ?: 0
        return Math.addExact(last, 1)
    }

    fun openVisit(table: Int) = visits.lastOrNull { it.table == table && !it.closed }

    fun serviceTables(): List<ServiceTable> = visits.map { it.table }.distinct().sorted().map { number ->
        val visit = openVisit(number)
        ServiceTable(number, visit?.id, visit?.responsible ?: visit?.openedBy, visit?.openedAt, visit?.orderNumber)
    }

    fun ordersBy(employeeId: String) = registeredOrders().ordersBy(employeeId)

    fun registeredOrders(): List<RegisteredOrder> = visits.mapNotNull { visit ->
        val number = visit.orderNumber ?: return@mapNotNull null
        val startedAt = visit.orderStartedAt ?: visit.dispatches.minOfOrNull { it.sentAt } ?: visit.openedAt ?: return@mapNotNull null
        val lastDispatchActivity = visit.dispatches.maxOfOrNull { it.lastActivityAt } ?: startedAt
        val lastActivity = visit.cashierHandoff?.sentAt?.let { maxOf(it, lastDispatchActivity) } ?: lastDispatchActivity
        val activeLines = visit.dispatches.flatMap { it.activeLines }
        val ready = activeLines.sumOf { if (it.kitchenStatus == KitchenItemStatus.READY) it.quantity.toLong() else 0L }
        val status = when {
            visit.isDiscardedDraft -> RegisteredOrderStatus.CANCELED
            visit.closed -> RegisteredOrderStatus.CLOSED
            ready > 0 -> RegisteredOrderStatus.READY
            activeLines.any { it.kitchenStatus == KitchenItemStatus.IN_PREPARATION } -> RegisteredOrderStatus.PREPARING
            visit.draft.isNotEmpty() -> RegisteredOrderStatus.DRAFT
            activeLines.any { it.dispatchStatus == KitchenItemStatus.SENT } -> RegisteredOrderStatus.SENT
            activeLines.isNotEmpty() && activeLines.all { it.kitchenStatus == KitchenItemStatus.DELIVERED } ->
                RegisteredOrderStatus.DELIVERED
            else -> RegisteredOrderStatus.OPEN
        }
        val employeeIds = (visit.dispatches.mapNotNull { it.employeeId } + listOfNotNull(visit.openedBy?.id)).toSet()
        val readyProducts = if (status == RegisteredOrderStatus.READY) {
            activeLines.filter { it.kitchenStatus == KitchenItemStatus.READY }
                .groupBy { it.product }
                .map { (name, lines) -> ReadyProductSummary(name, lines.sumOf { it.quantity.toLong() }) }
        } else emptyList()
        RegisteredOrder(
            visit.id, visit.table, number, lastActivity, status, ready, employeeIds, readyProducts,
            startedAt = startedAt,
            tableName = visit.tableName,
            locationName = visit.locationName,
            tableCode = visit.tableCode,
            products = visit.dispatches.flatMap { dispatch -> dispatch.lines.map { line ->
                line to if (dispatch.canceledAt != null) KitchenItemStatus.CANCELED else line.dispatchStatus
            } }.filter { it.first.quantity > 0 }.groupBy { it.first.product to it.second }.map { (key, lines) ->
                OrderProductSummary(key.first, lines.sumOf { it.first.quantity.toLong() }, key.second)
            },
            draftProducts = visit.draft.filter { it.quantity > 0 }.groupBy { it.product }.map { (name, lines) ->
                DraftProductSummary(name, lines.sumOf { it.quantity.toLong() })
            },
            hasDraftProducts = visit.draft.isNotEmpty(),
            hasKitchenDispatches = visit.dispatches.isNotEmpty(),
            cashierHandoffAt = visit.cashierHandoff?.sentAt
        )
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

    fun add(visitId: String, line: OrderLine, addedAt: Instant = Instant.now(), startedBy: TableWorker? = null) = changeVisit(visitId) { visit ->
        visit.startDraft(addedAt, startedBy).copy(draft = visit.draft.addCartLine(line))
    }

    fun addDraftLines(visitId: String, lines: List<OrderLine>, addedAt: Instant = Instant.now(), startedBy: TableWorker? = null): TableService {
        check(visits.any { it.id == visitId && !it.closed })
        if (lines.isEmpty()) return this
        return changeVisit(visitId) { visit ->
            visit.startDraft(addedAt, startedBy).copy(draft = lines.fold(visit.draft) { draft, line -> draft.addCartLine(line) })
        }
    }

    private fun TableVisit.startDraft(at: Instant, worker: TableWorker?) = if (orderNumber != null) copy(numberReserved = false) else copy(
        orderNumber = nextTicketNumber(), orderStartedAt = orderStartedAt ?: at,
        openedBy = worker ?: openedBy, responsible = responsible ?: worker ?: openedBy
    )

    /** Migra borradores ya guardados sin crear otra atención ni inventar una hora anterior. */
    fun ensureDraftOrders(): TableService {
        var service = this
        visits.filter { it.draft.isNotEmpty() && it.orderNumber == null }.forEach { pending ->
            val number = service.nextTicketNumber()
            service = service.copy(visits = service.visits.map { visit ->
                if (visit.id != pending.id) visit else visit.copy(
                    orderNumber = number,
                    orderStartedAt = visit.orderStartedAt ?: visit.dispatches.minOfOrNull { it.sentAt } ?: visit.openedAt
                )
            })
        }
        return service
    }

    fun adjustDraft(visitId: String, line: OrderLine, delta: Int) = changeVisit(visitId) { visit ->
        val draft = visit.draft.adjustCartLine(line, delta)
        visit.copy(draft = draft,
            closed = visit.orderNumber != null && draft.isEmpty() && visit.dispatches.isEmpty())
    }

    /** Repara atenciones del flujo anterior; una mesa solo seleccionada aún no tiene número. */
    fun closeEmptyDraftOrders() = copy(visits = visits.map { visit ->
        if (!visit.closed && visit.orderNumber != null && !visit.hasRecordedProducts && !visit.numberReserved) {
            visit.copy(closed = true)
        } else visit
    })

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

    fun advanceOrderLine(key: DispatchLineKey, expected: KitchenItemStatus, at: Instant) = when (expected) {
        KitchenItemStatus.SENT, KitchenItemStatus.IN_PREPARATION -> advanceKitchenLine(key, expected, at)
        KitchenItemStatus.READY -> deliverLine(key, at)
        else -> this
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
    val readyQuantity: Long,
    val employeeIds: Set<String>,
    val readyProducts: List<ReadyProductSummary> = emptyList(),
    val startedAt: Instant = lastActivityAt,
    val tableName: String? = null,
    val locationName: String? = null,
    val tableCode: String? = null,
    val products: List<OrderProductSummary> = emptyList(),
    val hasDraftProducts: Boolean = false,
    val hasKitchenDispatches: Boolean = products.isNotEmpty(),
    val cashierHandoffAt: Instant? = null,
    val draftProducts: List<DraftProductSummary> = emptyList()
) {
    val hasDisplayableProducts get() = products.any { it.quantity > 0 } || draftProducts.any { it.quantity > 0 }
    val isFullyServed get() = !hasDraftProducts && draftProducts.none { it.quantity > 0 } &&
        products.any { it.quantity > 0 && it.status != KitchenItemStatus.CANCELED } &&
        products.filter { it.quantity > 0 && it.status != KitchenItemStatus.CANCELED }
            .all { it.status == KitchenItemStatus.DELIVERED }
    val isFullyReady get() = status == RegisteredOrderStatus.READY && !hasDraftProducts && draftProducts.isEmpty() && products.isNotEmpty() &&
        products.all { it.status in setOf(KitchenItemStatus.READY, KitchenItemStatus.DELIVERED, KitchenItemStatus.CANCELED) }
}

internal data class ReadyProductSummary(val name: String, val quantity: Long)
internal data class OrderProductSummary(val name: String, val quantity: Long, val status: KitchenItemStatus)
internal data class DraftProductSummary(val name: String, val quantity: Long)

internal enum class RegisteredOrderStatus { OPEN, DRAFT, SENT, PREPARING, READY, DELIVERED, CLOSED, CANCELED }

internal fun List<RegisteredOrder>.ordersBy(employeeId: String) = filter { employeeId in it.employeeIds }

internal data class ServiceTable(
    val number: Int,
    val visitId: String?,
    val responsible: TableWorker?,
    val openedAt: Instant?,
    val orderNumber: Int?
)

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
