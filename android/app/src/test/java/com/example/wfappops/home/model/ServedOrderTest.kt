package com.example.wfappops.home.model

import com.example.wfappops.orders.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ServedOrderTest {
    private fun order(vararg statuses: KitchenItemStatus) = RegisteredOrder(
        "visit", 1, 1, Instant.EPOCH, RegisteredOrderStatus.DELIVERED, 0, setOf("worker"),
        products = statuses.map { OrderProductSummary("Producto", 1, it) }
    )

    @Test fun greenRequiresEveryValidProductToBeServedWithoutDrafts() {
        assertFalse(order(KitchenItemStatus.READY).isFullyServed)
        assertFalse(order(KitchenItemStatus.DELIVERED, KitchenItemStatus.READY).isFullyServed)
        val served = order(KitchenItemStatus.DELIVERED, KitchenItemStatus.CANCELED)
        assertTrue(served.isFullyServed)
        assertTrue(served.copy(status = RegisteredOrderStatus.CLOSED).isFullyServed)
        assertFalse(served.copy(hasDraftProducts = true).isFullyServed)
        assertFalse(served.copy(draftProducts = listOf(DraftProductSummary("Pendiente", 1))).isFullyServed)
        assertFalse(order(KitchenItemStatus.CANCELED).isFullyServed)
        assertFalse(order().isFullyServed)
    }
}
