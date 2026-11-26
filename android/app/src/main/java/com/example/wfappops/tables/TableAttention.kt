package com.example.wfappops.tables

import java.time.Instant
import com.example.wfappops.orders.model.TableService

internal data class TableReference(val locationId: String, val tableId: String)

/** Presentación de una atención confirmada; null significa que la fuente no entrega ocupación. */
internal sealed interface TableAttention {
    data object Free : TableAttention
    data class Occupied(
        val attendant: TableAttendant?, val startedAt: Instant?,
        val visitId: String? = null, val internalTable: Int? = null
    ) : TableAttention
}

/** Mantiene nombre y apellido separados para no adivinar apellidos a partir de display_name. */
internal data class TableAttendant(val firstName: String, val lastName: String) {
    val fullName: String get() = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")
    val shortName: String get() = listOfNotNull(
        firstName.trim().takeIf { it.isNotEmpty() },
        lastName.trim().firstOrNull()?.uppercase()?.let { "$it." }
    ).joinToString(" ")

    companion object {
        /** Con nombres compuestos conserva el texto completo en vez de adivinar un apellido. */
        fun fromDisplayName(name: String): TableAttendant {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return if (parts.size == 2) TableAttendant(parts[0], parts[1]) else TableAttendant(parts.joinToString(" "), "")
        }
    }
}

/** Solo refleja atenciones que este equipo conoce; la ausencia de registro no afirma disponibilidad global. */
internal fun TableService.localTableAttentions(): Map<TableReference, TableAttention> = visits
    .filter { (it.apiBacked || it.orderNumber != null || it.draft.isNotEmpty()) && !it.remoteTableId.isNullOrBlank() && !it.locationId.isNullOrBlank() }
    .groupBy { TableReference(checkNotNull(it.locationId), checkNotNull(it.remoteTableId)) }
    .mapValues { (_, history) ->
        val visit = history.lastOrNull { !it.closed && it.remoteOrderId != null } ?: history.lastOrNull { !it.closed } ?: history.last()
        if (visit.closed || (!visit.hasRecordedProducts && visit.remoteOrderId == null)) TableAttention.Free else TableAttention.Occupied(
            attendant = visit.openedBy?.let { TableAttendant.fromDisplayName(it.name) },
            startedAt = visit.orderStartedAt ?: visit.dispatches.minOfOrNull { it.sentAt } ?: visit.openedAt,
            visitId = visit.id, internalTable = visit.table
        )
    }
