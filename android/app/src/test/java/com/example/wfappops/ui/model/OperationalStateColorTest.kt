package com.example.wfappops.ui.model

import com.example.wfappops.orders.model.*
import com.example.wfappops.tables.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationalStateColorTest {
    private fun order(status: RegisteredOrderStatus, vararg products: KitchenItemStatus) = RegisteredOrder(
        "visit", 1, 1, Instant.EPOCH, status, 0, setOf("worker"),
        products = products.map { OrderProductSummary("Producto", 1, it) }
    )

    @Test fun closedAndCanceledTakePriorityOverServedProducts() {
        assertEquals(OperationalStateColor.RED, order(RegisteredOrderStatus.CLOSED, KitchenItemStatus.DELIVERED).operationalStateColor())
        assertEquals(OperationalStateColor.GRAY, order(RegisteredOrderStatus.CANCELED).operationalStateColor())
        assertEquals(OperationalStateColor.GREEN, order(RegisteredOrderStatus.DELIVERED, KitchenItemStatus.DELIVERED).operationalStateColor())
    }

    @Test fun readyPreparingAndDraftProductsRemainOrange() {
        assertEquals(OperationalStateColor.ORANGE, order(RegisteredOrderStatus.READY, KitchenItemStatus.READY).operationalStateColor())
        assertEquals(OperationalStateColor.ORANGE, order(RegisteredOrderStatus.PREPARING, KitchenItemStatus.IN_PREPARATION).operationalStateColor())
        assertEquals(OperationalStateColor.ORANGE, order(RegisteredOrderStatus.DRAFT).operationalStateColor())
        val servedWithDraft = order(RegisteredOrderStatus.DELIVERED, KitchenItemStatus.DELIVERED).copy(hasDraftProducts = true)
        assertEquals(OperationalStateColor.ORANGE, servedWithDraft.operationalStateColor())
    }

    @Test fun servingAnOrderDoesNotReleaseItsTableButClosingDoes() {
        val worker = TableWorker("1", "Colaborador")
        val time = Instant.EPOCH
        val service = TableService(listOf(TableVisit("visit", 1, remoteTableId = "99", locationId = "10",
            openedBy = worker, orderNumber = 1, orderStartedAt = time,
            dispatches = listOf(OrderDispatch(time, worker.name,
                listOf(OrderLine("Café", 1, 100, kitchenStatus = KitchenItemStatus.DELIVERED)), worker.id)))))
        val reference = TableReference("10", "99")
        assertEquals(OperationalStateColor.GREEN, service.registeredOrders().single().operationalStateColor())
        assertEquals(OperationalStateColor.ORANGE, service.localTableAttentions().getValue(reference).operationalStateColor())
        val closed = service.sendToCashier("visit", worker, time.plusSeconds(1))
        assertEquals(OperationalStateColor.RED, closed.registeredOrders().single().operationalStateColor())
        assertEquals(OperationalStateColor.GREEN, closed.localTableAttentions().getValue(reference).operationalStateColor())
    }
}
