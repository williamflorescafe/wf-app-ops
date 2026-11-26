package com.example.wfappops.carta

import java.math.BigDecimal

internal data class CartaModifierGroup(
    val id: String,
    val label: String,
    val multiple: Boolean,
    val minSelections: Int,
    val maxSelections: Int,
    val options: List<CartaModifierOption>,
    val allowsRepeat: Boolean = false,
    val variantId: String? = null
) {
    fun defaults(): List<String> = options.filter { it.available && it.isDefault }.take(maxSelections).map { it.id }

    fun selectedOptions(selection: List<String>): List<CartaModifierOption> =
        selection.mapNotNull { id -> options.firstOrNull { it.available && it.id == id } }

    fun select(selection: List<String>, option: CartaModifierOption): List<String> = when {
        maxSelections == 0 -> emptyList()
        !option.available -> selection
        !multiple -> if (minSelections == 0 && selection == listOf(option.id)) emptyList() else listOf(option.id)
        allowsRepeat && selection.size < maxSelections -> selection + option.id
        allowsRepeat -> selection
        option.id in selection -> selection - option.id
        selection.size < maxSelections -> selection + option.id
        else -> selection
    }

    fun adjust(selection: List<String>, option: CartaModifierOption, delta: Int): List<String> {
        if (!option.available) return selection
        return when {
            delta > 0 && selection.size < maxSelections && (allowsRepeat || option.id !in selection) -> selection + option.id
            delta < 0 && option.id in selection -> selection.toMutableList().apply { remove(option.id) }
            else -> selection
        }
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

internal fun formatCartaPrice(cents: Long, currencyCode: String = "PEN"): String {
    val currency = java.util.Currency.getInstance(currencyCode)
    val prefix = if (currencyCode == "PEN") "S/" else currencyCode
    return "$prefix ${BigDecimal.valueOf(cents, currency.defaultFractionDigits.coerceAtLeast(0)).toPlainString()}"
}
