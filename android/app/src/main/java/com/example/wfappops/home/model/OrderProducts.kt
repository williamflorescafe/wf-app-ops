package com.example.wfappops.home.model

import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.RegisteredOrder

/** Todos conserva los enviados anulados; los filtros operativos solo incluyen sus estados vigentes. */
internal fun RegisteredOrder.productsFor(filter: HomeOrderFilter) = products.filter {
    when (filter) {
        HomeOrderFilter.ALL -> true
        HomeOrderFilter.PREPARING -> it.status == KitchenItemStatus.SENT || it.status == KitchenItemStatus.IN_PREPARATION
        HomeOrderFilter.READY -> it.status == KitchenItemStatus.READY
    }
}
