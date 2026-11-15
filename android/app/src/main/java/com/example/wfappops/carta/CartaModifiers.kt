package com.example.wfappops.carta

import java.math.BigDecimal

internal data class CartaModifierGroup(
    val id: String,
    val label: String,
    val multiple: Boolean,
    val minSelections: Int,
    val maxSelections: Int,
    val options: List<CartaModifierOption>
) {
    fun defaults(): List<String> = options.filter { it.available && it.isDefault }.take(maxSelections).map { it.id }

    fun selectedOptions(selection: List<String>): List<CartaModifierOption> =
        options.filter { it.available && it.id in selection }

    fun select(selection: List<String>, option: CartaModifierOption): List<String> = when {
        !option.available -> selection
        !multiple -> listOf(option.id)
        option.id in selection -> selection - option.id
        selection.size < maxSelections -> selection + option.id
        else -> selection
    }
}

internal data class CartaModifierOption(
    val id: String,
    val label: String,
    val priceDeltaInCents: Long,
    val image: String? = null,
    val isDefault: Boolean = false,
    val available: Boolean = true
)

internal fun formatCartaPrice(cents: Long): String = "S/ ${BigDecimal.valueOf(cents, 2).toPlainString()}"
