package com.example.wfappops.carta

import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.OrderModifierSelection

internal fun CartaProduct.orderLine(variantId: String?, selections: Map<String, List<String>>,
                                    quantity: Int = 1, note: String? = null): OrderLine {
    require(canOrder && quantity > 0)
    val variant = if (locationId != null) checkNotNull(variants.firstOrNull { it.id == variantId && it.available }) else null
    val groups = groupsFor(variant?.id)
    require(selections.keys.all { selectedGroup -> groups.any { it.id == selectedGroup } })
    var unitPrice = variant?.priceMinor ?: priceInCents
    val chosen = mutableListOf<OrderModifierSelection>()
    val labels = mutableListOf<String>()
    variant?.name?.takeIf { it.isNotBlank() }?.let(labels::add)
    groups.forEach { group ->
        val ids = selections[group.id].orEmpty()
        require(ids.size in group.minSelections..group.maxSelections)
        require(group.allowsRepeat || ids.distinct().size == ids.size)
        ids.groupingBy { it }.eachCount().toSortedMap().forEach { (id, units) ->
            val option = checkNotNull(group.options.firstOrNull { it.id == id && it.available })
            unitPrice = Math.addExact(unitPrice, Math.multiplyExact(option.priceDeltaInCents, units.toLong()))
            chosen += OrderModifierSelection(group.id, option.id, units)
            labels += if (units == 1) option.label else "${option.label} ×$units"
        }
    }
    require(unitPrice >= 0)
    return OrderLine(name, quantity, unitPrice, labels, note?.trim()?.ifBlank { null },
        productId = id.takeIf { locationId != null }, variantId = variant?.id,
        modifierSelections = chosen.sortedWith(compareBy({ it.groupId }, { it.optionId })),
        currencyCode = variant?.currencyCode ?: currencyCode, locationId = locationId, menuId = menuId)
}

internal fun OpsMenu.validateOrderLine(line: OrderLine): OrderLine {
    require(line.locationId == location.id && line.menuId == id)
    val product = checkNotNull(collections.asSequence().flatMap { it.sections.asSequence() }
        .flatMap { it.products.asSequence() }.firstOrNull { it.id == line.productId })
    val selected = line.modifierSelections.groupBy { it.groupId }.mapValues { (_, options) ->
        options.flatMap { option ->
            require(option.quantity in 1..1000)
            List(option.quantity) { option.optionId }
        }
    }
    val confirmed = product.orderLine(line.variantId, selected, line.quantity, line.note)
    require(confirmed.unitPriceInCents == line.unitPriceInCents && confirmed.currencyCode == line.currencyCode)
    return confirmed
}
