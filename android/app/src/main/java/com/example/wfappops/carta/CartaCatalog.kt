package com.example.wfappops.carta

import android.content.res.AssetManager
import com.example.wfappops.carta.data.catalogHttpsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

internal data class CartaCollection(
    val id: String,
    val name: String,
    val photo: String,
    val sections: List<CartaSection>
)

internal data class CartaSection(
    val id: String,
    val name: String,
    val products: List<CartaProduct>
)

internal data class CartaProduct(
    val id: String,
    val name: String,
    val description: String,
    val priceInCents: Long,
    val photos: List<String>,
    val modifierGroups: List<CartaModifierGroup>
) {
    val price get() = formatCartaPrice(priceInCents)
}

internal suspend fun loadCarta(assets: AssetManager): Result<List<CartaCollection>> =
    loadCatalog(assets, "carta/catalogo.json") { catalog, customizations ->
        listOf(
            catalog.getJSONArray("bebidas").collection(
                "bebidas", "Café de especialidad", "banners/cafe-de-especialidad.png", customizations
            ),
            catalog.getJSONArray("postres").collection(
                "postres", "Pastelería fina", "banners/pasteleria-fina-un-plato.png", customizations
            )
        )
    }

internal suspend fun loadOrderCarta(assets: AssetManager): Result<List<CartaCollection>> =
    loadCatalog(assets, "carta/catalogo-pedidos.json") { catalog, customizations ->
        val collections = catalog.getJSONArray("collections")
        List(collections.length()) { index ->
            val collection = collections.getJSONObject(index)
            collection.getJSONArray("sections").collection(
                collection.getString("id"), collection.getString("name"), "", customizations
            )
        }
    }

internal fun parseRemoteOrderCarta(payload: JSONObject, assets: AssetManager): List<CartaCollection> {
    val categories = payload.getJSONObject("data").getJSONArray("categories")
    val customizations by lazy { assets.readJson("carta/personalizacion.json") }
    val categoryIds = mutableSetOf<String>()
    val productIds = mutableSetOf<String>()
    return buildList {
        for (index in 0 until categories.length()) {
            val category = categories.getJSONObject(index)
            if (category.optString("type") == "promotions") continue
            val id = category.getString("id")
            require(id.isNotBlank() && categoryIds.add(id))
            val subcategories = category.getJSONArray("subcategories")
            val sectionIds = mutableSetOf<String>()
            val sections = List(subcategories.length()) { sectionIndex ->
                val section = subcategories.getJSONObject(sectionIndex)
                val sectionId = section.getString("id")
                require(sectionId.isNotBlank() && sectionIds.add(sectionId))
                val products = section.getJSONArray("products")
                CartaSection(
                    id = "$id/$sectionId",
                    name = section.getString("name"),
                    products = List(products.length()) { productIndex ->
                        val product = products.getJSONObject(productIndex)
                        val productId = product.getString("id")
                        require(productId.isNotBlank() && productIds.add(productId))
                        val groups = if (product.getBoolean("has_modifiers")) {
                            checkNotNull(customizations.optJSONArray(product.getString("slug"))) {
                                "Missing modifiers for catalog product $productId"
                            }.modifierGroups().also { require(it.isNotEmpty()) }
                        } else emptyList()
                        CartaProduct(
                            id = productId,
                            name = product.getString("name"),
                            description = product.getString("description"),
                            priceInCents = product.priceInCents("price").also { require(it >= 0) },
                            photos = emptyList(),
                            modifierGroups = groups
                        )
                    }
                )
            }
            val photo = category.optString("image").let { catalogHttpsUrl(it)?.toExternalForm() }.orEmpty()
            add(CartaCollection(id, category.getString("label"), photo, sections))
        }
    }
}

private suspend fun loadCatalog(
    assets: AssetManager,
    path: String,
    collections: (JSONObject, JSONObject) -> List<CartaCollection>
): Result<List<CartaCollection>> =
    withContext(Dispatchers.IO) {
        runCatching {
            collections(assets.readJson(path), assets.readJson("carta/personalizacion.json"))
        }
    }

private fun AssetManager.readJson(path: String): JSONObject =
    open(path).bufferedReader().use { JSONObject(it.readText()) }

private fun JSONArray.collection(
    id: String,
    name: String,
    photo: String,
    customizations: JSONObject
): CartaCollection {
    val sections = List(length()) { sectionIndex ->
        val category = getJSONObject(sectionIndex)
        val sectionId = category.getString("id")
        val items = category.getJSONArray("items")
        val products = List(items.length()) { itemIndex ->
            val item = items.getJSONObject(itemIndex)
            val gallery = item.optJSONArray("gallery") ?: JSONArray()
            val groups = if (item.optBoolean("hasModifiers")) {
                customizations.optJSONArray(item.optString("slug"))?.modifierGroups().orEmpty()
            } else {
                emptyList()
            }
            CartaProduct(
                id = "$sectionId/${item.optString("id", itemIndex.toString())}",
                name = item.getString("name"),
                description = item.getString("desc"),
                priceInCents = item.priceInCents("price"),
                photos = List(gallery.length()) { photoIndex ->
                    "carta/${gallery.getJSONObject(photoIndex).getString("w600")}"
                },
                modifierGroups = groups
            )
        }
        CartaSection(sectionId, category.getString("name"), products)
    }
    return CartaCollection(id, name, photo, sections)
}

private fun JSONArray.modifierGroups(): List<CartaModifierGroup> = List(length()) { index ->
    val group = getJSONObject(index)
    val options = group.getJSONArray("options")
    val multiple = group.getString("selectionType") == "multiple"
    val minimum = if (group.optBoolean("required")) 1 else 0
    CartaModifierGroup(
        id = group.getString("id"),
        label = group.getString("label"),
        multiple = multiple,
        minSelections = maxOf(minimum, group.optInt("minSelections", minimum)),
        maxSelections = if (multiple) group.optInt("maxSelections", options.length()) else 1,
        options = List(options.length()) { optionIndex ->
            val option = options.getJSONObject(optionIndex)
            CartaModifierOption(
                id = option.getString("id"),
                label = option.getString("label"),
                priceDeltaInCents = option.priceInCents("priceDelta"),
                image = option.optString("image").takeIf { group.optBoolean("hasImages") && it.isNotBlank() },
                isDefault = option.optBoolean("isDefault"),
                available = option.optBoolean("available", true)
            )
        }
    )
}

private fun JSONObject.priceInCents(key: String): Long = getString(key).toBigDecimal().movePointRight(2).longValueExact()
