package com.example.wfappops.orders.model

import java.time.Instant

/** Una secuencia del dispositivo por cuenta: no renumera los borradores que siguen abiertos. */
internal fun TableService.withLocalDraftNumbers(previousNext: Int = nextLocalDraftNumber): TableService {
    val active = visits.withIndex().filter { (_, visit) -> visit.isNumberedLocalDraft }
    if (active.isEmpty()) return copy(nextLocalDraftNumber = 1,
        visits = visits.map { if (it.localDraftNumber == null) it else it.copy(localDraftNumber = null) })
    var next = maxOf(nextLocalDraftNumber, previousNext,
        Math.addExact(active.maxOfOrNull { it.value.localDraftNumber ?: 0 } ?: 0, 1))
    val assigned = active.filter { it.value.localDraftNumber == null }.sortedWith(
        compareBy<IndexedValue<TableVisit>> { it.value.openedAt ?: it.value.orderStartedAt ?: Instant.EPOCH }
            .thenBy { it.index }
    ).associate { indexed ->
        val number = next
        next = Math.addExact(next, 1)
        indexed.value.id to number
    }
    return copy(nextLocalDraftNumber = next, visits = visits.map { visit ->
        val number = if (visit.isNumberedLocalDraft) visit.localDraftNumber ?: assigned.getValue(visit.id) else null
        if (number == visit.localDraftNumber) visit else visit.copy(localDraftNumber = number)
    })
}

private val TableVisit.isNumberedLocalDraft get() = apiBacked && !closed && remoteOrderId == null &&
    orderNumber == null && dispatches.isEmpty() && draft.any { it.quantity > 0 }
