package com.example.wfappops.home.model

import com.example.wfappops.orders.model.TableService
import com.example.wfappops.tables.tableZone
import java.time.Instant
import java.time.LocalDate

internal enum class HomeOrderFilter { ALL, PREPARING, READY }

/** Límites del día en la sede: incluye medianoche y excluye la del día siguiente. */
internal data class OrderDay(val date: LocalDate) {
    val start: Instant = date.atStartOfDay(tableZone).toInstant()
    val end: Instant = date.plusDays(1).atStartOfDay(tableZone).toInstant()

    operator fun contains(instant: Instant) = instant >= start && instant < end
}

/** Dos días calendario en Lima: hoy y ayer, sin incluir la medianoche de mañana. */
internal data class OrderHistoryWindow(val today: LocalDate) {
    val start: Instant = today.minusDays(1).atStartOfDay(tableZone).toInstant()
    val end: Instant = today.plusDays(1).atStartOfDay(tableZone).toInstant()

    operator fun contains(instant: Instant) = instant >= start && instant < end
}

/** Prepara el historial reciente y mantiene las atenciones abiertas para el inicio operativo. */
internal fun TableService.operationalOrders(window: OrderHistoryWindow) = copy(
    visits = visits.filter { visit ->
        !visit.closed || (visit.orderStartedAt ?: visit.dispatches.minOfOrNull { it.sentAt })?.let { it in window } == true
    }
).registeredOrders()
