package com.example.wfappops.carta

import org.junit.Assert.assertEquals
import org.junit.Test

class CartaModifiersTest {
    private val small = CartaModifierOption("peq", "Pequeño", 0)
    private val tall = CartaModifierOption("alto", "Alto", 200, isDefault = true)
    private val unavailable = CartaModifierOption("gran", "Grande", 400, isDefault = true, available = false)

    @Test
    fun singleSelectionStartsWithAvailableDefaultAndReplacesIt() {
        val group = CartaModifierGroup("tamano", "Tamaño", false, 1, 1, listOf(small, tall, unavailable))

        assertEquals(listOf("alto"), group.defaults())
        assertEquals(listOf("peq"), group.select(group.defaults(), small))
        assertEquals(group.defaults(), group.select(group.defaults(), unavailable))
    }

    @Test
    fun multipleSelectionCanRemoveOptionsAfterReachingTheLimit() {
        val group = CartaModifierGroup("extras", "Extras", true, 0, 1, listOf(small, tall))
        val fullSelection = listOf("peq")

        assertEquals(fullSelection, group.select(fullSelection, tall))
        assertEquals(emptyList<String>(), group.select(fullSelection, small))
    }

    @Test
    fun priceKeepsTwoDecimalPlaces() {
        assertEquals("S/ 14.05", formatCartaPrice(1405))
    }
}
