package com.example.wfappops.carta

import com.example.wfappops.auth.model.TeamLocation
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal data class OpsMenu(val location: TeamLocation, val id: String?, val name: String?,
                            val generatedAt: Instant, val collections: List<CartaCollection>)

/** Contrato de Salón de OPS; no completa campos con el catálogo o personalizador local. */
internal fun parseOpsMenu(payload: String, expectedLocationId: String): OpsMenu {
    val data = JSONObject(payload).getJSONObject("data")
    val location = data.getJSONObject("location")
    val locationId = location.requiredId("id")
    require(locationId == expectedLocationId)
    val local = TeamLocation(location.getString("name"), location.getString("code"), locationId)
    val generatedAt = Instant.parse(data.getString("generated_at"))
    if (data.isNull("menu")) return OpsMenu(local, null, null, generatedAt, emptyList())
    val menu = data.getJSONObject("menu")
    val menuId = menu.requiredId("id")
    val groupDefinitions = data.getJSONArray("modifier_groups").objects().associateUnique { it.requiredId("id") }
    val knownProducts = mutableMapOf<String, CartaProduct>()

    fun product(json: JSONObject): CartaProduct {
        val id = json.requiredId("id")
        val variants = json.getJSONArray("variants").objects().map { variant ->
            val currency = variant.getString("currency_code")
            java.util.Currency.getInstance(currency)
            CartaVariant(variant.requiredId("id"), variant.getString("name"), variant.minor("price_minor").also { require(it >= 0) },
                currency, variant.getBoolean("available"))
        }
        require(variants.map { it.id }.distinct().size == variants.size)
        val groups = json.getJSONArray("modifier_groups").objects().map { binding ->
            val groupId = binding.requiredId("group_id")
            val definition = checkNotNull(groupDefinitions[groupId]) { "Unknown modifier group" }
            val variantId = if (binding.isNull("variant_id")) null else binding.requiredId("variant_id")
            require(variantId == null || variants.any { it.id == variantId })
            val min = Math.toIntExact(binding.minor("min_select"))
            val max = Math.toIntExact(binding.minor("max_select"))
            require(min >= 0 && max >= min)
            val options = definition.getJSONArray("options").objects().map { option ->
                CartaModifierOption(option.requiredId("id"), option.getString("name"), option.minor("price_delta_minor"),
                    isDefault = option.getBoolean("is_default"), available = option.getBoolean("available"))
            }
            require(options.map { it.id }.distinct().size == options.size)
            CartaModifierGroup(groupId, definition.getString("name"), max > 1, min, max, options,
                definition.getBoolean("allows_repeat"), variantId)
        }
        require(groups.map { it.id to it.variantId }.distinct().size == groups.size)
        val representative = variants.filter { it.available }.minByOrNull { it.priceMinor }
            ?: variants.minByOrNull { it.priceMinor }
        val result = CartaProduct(id, json.getString("name"), if (json.isNull("short_description")) "" else json.getString("short_description"),
            representative?.priceMinor ?: 0, emptyList(), groups, variants, json.getBoolean("available"),
            representative?.currencyCode ?: "PEN", locationId, menuId)
        knownProducts[id]?.let { require(it == result) { "Conflicting product definitions" } }
        knownProducts[id] = result
        return result
    }

    val categoryIds = mutableSetOf<String>()
    val collections = data.getJSONArray("categories").objects().map { category ->
        val categoryId = category.requiredId("id")
        require(categoryIds.add(categoryId))
        val sections = category.getJSONArray("subcategories").objects().map { section ->
            CartaSection(section.requiredId("id"), section.getString("name"), section.getJSONArray("products").objects().map(::product))
        }
        require(sections.map { it.id }.distinct().size == sections.size)
        val sectionProductIds = sections.flatMap { it.products }.map { it.id }.toSet()
        val direct = category.getJSONArray("products").objects().map(::product).filterNot { it.id in sectionProductIds }.distinctBy { it.id }
        val seen = mutableSetOf<String>()
        val normalized = buildList {
            if (direct.isNotEmpty()) add(CartaSection("$categoryId/direct", category.getString("name"), direct))
            addAll(sections.map { it.copy(products = it.products.filter { p -> seen.add(p.id) }) }.filter { it.products.isNotEmpty() })
        }
        CartaCollection(categoryId, category.getString("name"), "", normalized)
    }
    return OpsMenu(local, menuId, menu.getString("name"), generatedAt, collections)
}

private fun JSONObject.requiredId(key: String) = getString(key).also { require(it.isNotBlank() && it != "null") }
private fun JSONObject.minor(key: String): Long = get(key).toString().toBigDecimal().longValueExact()
private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private fun List<JSONObject>.associateUnique(key: (JSONObject) -> String): Map<String, JSONObject> {
    val result = associateBy(key)
    require(result.size == size)
    return result
}
