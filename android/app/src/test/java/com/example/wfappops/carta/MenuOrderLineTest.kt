package com.example.wfappops.carta

import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.orders.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MenuOrderLineTest {
    private val option = CartaModifierOption("almond", "Almendras", 200, isDefault = true)
    private val group = CartaModifierGroup("milk", "Leche", true, 0, 3, listOf(option), allowsRepeat = true)
    private val product = CartaProduct("coffee", "Café", "", 1000, emptyList(), listOf(group),
        listOf(CartaVariant("small", "Pequeño", 1000, "PEN", true), CartaVariant("big", "Grande", 1450, "PEN", true)),
        locationId = "10", menuId = "menu")
    private val menu = OpsMenu(TeamLocation("Local", "LOC", "10"), "menu", "Carta", Instant.EPOCH,
        listOf(CartaCollection("category", "Bebidas", "", listOf(CartaSection("section", "Café", listOf(product))))))

    @Test fun selectedVariantAndRepeatedOptionsKeepTheirIdsQuantitiesAndExactPrice() {
        val line = product.orderLine("big", mapOf("milk" to listOf("almond", "almond")), note = " Sin azúcar ")
        assertEquals(1850L, line.unitPriceInCents)
        assertEquals("coffee", line.productId)
        assertEquals("big", line.variantId)
        assertEquals("10", line.locationId)
        assertEquals("menu", line.menuId)
        assertEquals(listOf(OrderModifierSelection("milk", "almond", 2)), line.modifierSelections)
        assertEquals("Sin azúcar", line.note)
        assertEquals(line, menu.validateOrderLine(line))
        assertEquals("S/ 18.50", formatCartaPrice(line.unitPriceInCents, line.currencyCode))
    }

    @Test fun sameNamePriceOrNoteDoesNotMergeDifferentVariantsOrLegacyProducts() {
        val noGroups = product.copy(modifierGroups = emptyList(), variants = product.variants.map { it.copy(priceMinor = 1000) })
        val small = noGroups.orderLine("small", emptyMap()).copy(options = emptyList())
        val big = noGroups.orderLine("big", emptyMap()).copy(options = emptyList())
        val legacy = OrderLine("Café", 1, 1000)
        assertEquals(3, emptyList<OrderLine>().addCartLine(small).addCartLine(big).addCartLine(legacy).size)
        assertEquals(2, listOf(small).addCartLine(small).single().quantity)
    }

    @Test fun stockLimitsScopeAndChangedPriceAreValidatedBeforeSaving() {
        val line = product.orderLine("big", mapOf("milk" to listOf("almond")))
        assertTrue(runCatching { product.copy(available = false).orderLine("big", emptyMap()) }.isFailure)
        assertTrue(runCatching { product.orderLine("unknown", emptyMap()) }.isFailure)
        assertTrue(runCatching { product.orderLine("big", mapOf("milk" to List(4) { "almond" })) }.isFailure)
        assertTrue(runCatching { menu.validateOrderLine(line.copy(locationId = "11")) }.isFailure)
        assertTrue(runCatching { menu.validateOrderLine(line.copy(unitPriceInCents = 1)) }.isFailure)
        assertTrue(runCatching { menu.validateOrderLine(line.copy(variantId = "small")) }.isFailure)
    }

    @Test fun variantBindingOverridesOnlyTheMatchingGlobalGroup() {
        val specific = group.copy(maxSelections = 2, minSelections = 2, variantId = "big")
        val customized = product.copy(modifierGroups = listOf(group, specific))
        assertEquals(3, customized.groupsFor("small").single().maxSelections)
        assertEquals(2, customized.groupsFor("big").single().maxSelections)
        assertTrue(runCatching { customized.orderLine("big", mapOf("milk" to listOf("almond"))) }.isFailure)
    }

    @Test fun repeatLimitsCountUnitsAndAnOptionalSingleDefaultCanBeRemoved() {
        val twice = group.select(group.defaults(), option)
        assertEquals(2, group.selectedOptions(twice).size)
        val full = group.select(twice, option)
        assertEquals(3, full.size)
        assertEquals(full, group.select(full, option))
        assertEquals(2, group.adjust(full, option, -1).size)
        val single = group.copy(multiple = false, maxSelections = 1)
        assertEquals(emptyList<String>(), single.select(single.defaults(), option))
        assertTrue(group.copy(options = listOf(option.copy(available = false))).defaults().isEmpty())
    }

    @Test fun draftBindsItsLocalAndCannotBeMovedToAnotherLocalAfterAddingProducts() {
        val worker = TableWorker("worker", "Colaborador")
        val opened = TableService().createOrder(worker, Instant.EPOCH)
        val visit = opened.visits.single()
        val saved = opened.bindMenuLocation(visit.id, menu.location).add(visit.id,
            product.orderLine("big", emptyMap()), Instant.EPOCH, worker)
        assertEquals("10", saved.visits.single().locationId)
        assertTrue(runCatching { saved.bindMenuLocation(visit.id, menu.location.copy(id = "11")) }.isFailure)
    }
}
