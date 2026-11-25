package com.example.wfappops.ui.model

import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.RegisteredOrderStatus
import com.example.wfappops.tables.TableAttention

/** Categorías operativas; la paleta se resuelve aparte de las reglas del estado. */
internal enum class OperationalStateColor {
    /** Comanda pendiente de servir o mesa con atención abierta. */
    ORANGE,
    /** Comanda servida aún abierta o mesa libre. */
    GREEN,
    /** Comanda anulada. No representa una atención cerrada en caja. */
    GRAY,
    /** Atención cerrada o registrada para caja; tiene prioridad sobre productos servidos. */
    RED
}

internal fun RegisteredOrder.operationalStateColor(): OperationalStateColor = when {
    status == RegisteredOrderStatus.CLOSED -> OperationalStateColor.RED
    status == RegisteredOrderStatus.CANCELED -> OperationalStateColor.GRAY
    isFullyServed -> OperationalStateColor.GREEN
    else -> OperationalStateColor.ORANGE
}

/** La ocupación no cambia al servir productos; solo al finalizar la atención. */
internal fun TableAttention.operationalStateColor(): OperationalStateColor = when (this) {
    TableAttention.Free -> OperationalStateColor.GREEN
    is TableAttention.Occupied -> OperationalStateColor.ORANGE
}
