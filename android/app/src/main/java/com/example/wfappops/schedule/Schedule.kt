package com.example.wfappops.schedule

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal val limaZone: ZoneId = ZoneId.of("America/Lima")

internal data class ScheduledShift(
    val id: String,
    val date: LocalDate,
    val zone: ZoneId,
    val kind: String,
    val name: String,
    val start: Instant?,
    val end: Instant?,
    val breakMinutes: Int,
    val scheduledMinutes: Int
)

internal data class SchedulePage(val shifts: List<ScheduledShift>, val nextCursor: String?)

internal class ScheduleProtocolException(message: String) : IOException(message)

internal data class ScheduleSnapshot(val shifts: List<ScheduledShift>, val updatedAt: Instant)

internal enum class ScheduleIssue { CONNECTION, DENIED, UNAVAILABLE, CACHE, DEMO }

internal data class ScheduleState(
    val snapshot: ScheduleSnapshot? = null,
    val refreshing: Boolean = false,
    val issue: ScheduleIssue? = null
)

internal fun scheduleRetention(today: YearMonth): ClosedRange<YearMonth> = today.minusMonths(2)..today.plusMonths(1)

/** Devuelve el mes completo. Un cursor repetido o una fila fuera del rango invalidan la consulta. */
internal fun collectScheduleMonth(month: YearMonth, fetch: (String?) -> SchedulePage): List<ScheduledShift> {
    val shifts = mutableListOf<ScheduledShift>()
    val cursors = mutableSetOf<String>()
    var cursor: String? = null
    repeat(31) {
        val page = fetch(cursor)
        if (page.shifts.any { YearMonth.from(it.date) != month }) throw ScheduleProtocolException("Horario fuera del mes solicitado")
        shifts += page.shifts
        cursor = page.nextCursor ?: return shifts.distinctBy { it.id }.sortedBy { it.date }
        if (!cursors.add(checkNotNull(cursor))) throw ScheduleProtocolException("Cursor de horario repetido")
    }
    throw ScheduleProtocolException("Demasiadas páginas de horario")
}
