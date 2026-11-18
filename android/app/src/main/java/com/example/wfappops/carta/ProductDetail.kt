package com.example.wfappops.carta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.ui.components.WfButton
import com.example.wfappops.ui.components.WfButtonStyle
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfThemeExtras
import kotlinx.coroutines.launch

private const val ProductImageAspectRatio = 4f / 3f

private val selectionsSaver = listSaver<Map<String, List<String>>, ArrayList<String>>(
    save = { selections -> selections.map { (id, options) -> arrayListOf(id).apply { addAll(options) } } },
    restore = { entries -> entries.associate { it.first() to it.drop(1) } }
)

@Composable
internal fun ProductDetail(
    product: CartaProduct,
    collection: String,
    adding: Boolean,
    onAdd: (OrderLine) -> Unit,
    forOrder: Boolean = false
) {
    val groups = product.modifierGroups
    var selections by rememberSaveable(stateSaver = selectionsSaver) {
        mutableStateOf(groups.associate { it.id to it.defaults() })
    }
    var activeStep by rememberSaveable { mutableIntStateOf(0) }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var note by rememberSaveable(product.id) { mutableStateOf("") }
    val selected = groups.associate { it.id to it.selectedOptions(selections[it.id].orEmpty()) }
    val isReady = groups.all { selected.getValue(it.id).size >= it.minSelections }
    val unitPrice = product.priceInCents + selected.values.sumOf { options -> options.sumOf { it.priceDeltaInCents } }
    val total = formatCartaPrice(unitPrice * quantity)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val addProduct = {
        val options = groups.flatMap { group -> selected.getValue(group.id).map { it.label } }
        if (forOrder) focus.clearFocus()
        onAdd(OrderLine(product.name, if (forOrder) 1 else quantity, unitPrice, options, note.trim().ifEmpty { null }))
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                if (!forOrder) DetailGallery(product)
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)) {
                    ProductHeading(product, collection, if (forOrder) formatCartaPrice(unitPrice) else product.price)
                    if (forOrder) {
                        OrderNoteField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            label = stringResource(R.string.order_optional_note),
                            minLines = 2,
                            enabled = !adding
                        )
                    }
                    if (groups.isNotEmpty()) {
                        ProductCustomizer(
                            groups = groups,
                            selected = selected,
                            activeStep = activeStep,
                            onStep = { activeStep = it },
                            onSelect = { option ->
                                val group = groups[activeStep]
                                val updated = group.select(selected.getValue(group.id).map { it.id }, option)
                                selections = selections + (group.id to updated)
                                if (!group.multiple && activeStep < groups.lastIndex) activeStep++
                            }
                        )
                    }
                    if (forOrder) {
                        WfButton(
                            text = stringResource(R.string.order_add_product),
                            onClick = addProduct,
                            modifier = Modifier.padding(top = 16.dp),
                            style = WfButtonStyle.PURCHASE,
                            enabled = isReady && !adding,
                            fullWidth = true
                        )
                    }
                }
            }
            if (!forOrder && groups.isNotEmpty() && scroll.canScrollForward) {
                FilledIconButton(
                    onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        .size(WfSize.minTouchTarget)
                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back), stringResource(R.string.carta_go_to_options),
                        Modifier.rotate(-90f)
                    )
                }
            }
        }
        if (!forOrder) {
            ProductTotalBar(
                stringResource(R.string.cart_add, total),
                quantity, isReady && !adding, onQuantity = { quantity = it }, onAction = addProduct
            )
        }
    }
}

@Composable
private fun ProductHeading(product: CartaProduct, collection: String, price: String) {
    val scheme = MaterialTheme.colorScheme
    Text(collection, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    Text(
        product.name,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.headlineSmall,
        color = scheme.secondary
    )
    Text(
        price,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = scheme.outlineVariant)
    Text(
        stringResource(R.string.carta_description),
        color = scheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        product.description,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        color = scheme.primary,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun DetailGallery(product: CartaProduct) {
    Box(Modifier.fillMaxWidth()) {
        if (product.photos.isNotEmpty()) {
            val pager = rememberPagerState { product.photos.size }
            HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(ProductImageAspectRatio)) { index ->
                CartaPhoto(
                    product.photos[index],
                    stringResource(R.string.carta_gallery_photo, product.name, index + 1),
                    Modifier.fillMaxSize()
                )
            }
            if (product.photos.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(product.photos.size) { index ->
                        val current = pager.currentPage == index
                        Box(Modifier.size(if (current) 20.dp else 8.dp, 8.dp)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = if (current) 1f else 0.5f), CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductTotalBar(label: String, quantity: Int, ready: Boolean, onQuantity: (Int) -> Unit, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.primary, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = scheme.surface, contentColor = scheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val less = stringResource(R.string.carta_quantity_less)
                    val quantityColors = IconButtonDefaults.iconButtonColors(contentColor = scheme.primary)
                    IconButton(
                        onClick = { onQuantity(quantity - 1) }, enabled = quantity > 1,
                        modifier = Modifier.size(WfSize.minTouchTarget), colors = quantityColors
                    ) {
                        Text("−", Modifier.semantics { contentDescription = less })
                    }
                    Text(quantity.toString(), fontWeight = FontWeight.SemiBold)
                    IconButton(
                        onClick = { onQuantity(quantity + 1) }, enabled = quantity < Int.MAX_VALUE,
                        modifier = Modifier.size(WfSize.minTouchTarget), colors = quantityColors
                    ) {
                        Icon(painterResource(R.drawable.ic_carta_add), stringResource(R.string.carta_quantity_more))
                    }
                }
            }
            Button(
                onClick = onAction,
                enabled = ready,
                modifier = Modifier.weight(1f).heightIn(min = WfSize.minTouchTarget),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WfThemeExtras.purchaseAction,
                    contentColor = WfThemeExtras.onPurchaseAction,
                    disabledContainerColor = scheme.outlineVariant,
                    disabledContentColor = scheme.onSurfaceVariant
                )
            ) {
                Text(label, textAlign = TextAlign.Center)
            }
        }
    }
}
