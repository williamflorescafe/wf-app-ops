package com.example.wfappops.carta

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.tables.OrderLine
import com.example.wfappops.ui.theme.WfRadius
import com.example.wfappops.ui.theme.WfSize
import com.example.wfappops.ui.theme.WfSpacing
import com.example.wfappops.ui.theme.WfThemeExtras
import kotlinx.coroutines.launch
import java.text.Normalizer

private val diacritics = Regex("\\p{M}+")

private fun searchKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderProductSheet(
    lines: List<OrderLine>,
    busy: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onAdd: (OrderLine, () -> Unit) -> Unit
) {
    val assets = LocalContext.current.assets
    var retry by remember { mutableIntStateOf(0) }
    val catalog by produceState<Result<List<CartaCollection>>?>(null, assets, retry) {
        value = null
        value = loadOrderCarta(assets)
    }
    val collections = catalog?.getOrNull().orEmpty()
    val quantities = remember(lines) {
        lines.groupingBy { it.product }.fold(0L) { count, line -> count + line.quantity }
    }
    val searchField = rememberTextFieldState()
    val query = searchField.text.toString()
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val activeCategory = collections.firstOrNull { it.id == categoryId }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember(collections, selectedId) {
        selectedId?.let { id ->
            collections.firstNotNullOfOrNull { collection ->
                collection.sections.asSequence().flatMap { it.products.asSequence() }
                    .firstOrNull { it.id == id }?.let { collection.name to it }
            }
        }
    }
    val searchableNames = remember(collections) {
        collections.flatMap { it.sections }.flatMap { it.products }
            .associate { it.id to searchKey(it.name) }
    }
    val matching = remember(collections, searchableNames, categoryId, query) {
        val words = searchKey(query.trim()).split(Regex("\\s+")).filter(String::isNotEmpty)
        collections.filter { categoryId == null || it.id == categoryId }.mapNotNull { collection ->
            val sections = collection.sections.mapNotNull { section ->
                val products = section.products.filter { product ->
                    words.all { searchableNames.getValue(product.id).contains(it) }
                }
                section.copy(products = products).takeIf { products.isNotEmpty() }
            }
            collection.copy(sections = sections).takeIf { sections.isNotEmpty() }
        }
    }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    LaunchedEffect(categoryId, query) {
        listState.scrollToItem(0)
    }
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (selected == null) scheme.primary else scheme.surfaceContainerLow,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = if (selected == null) scheme.onPrimary else scheme.onSurfaceVariant)
        }
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            if (selected != null) {
                Column(Modifier.fillMaxSize().padding(horizontal = WfSpacing.lg)) {
                    if (failed) Text(stringResource(R.string.cart_error), color = scheme.error)
                    TextButton(
                        onClick = { selectedId = null },
                        modifier = Modifier.sizeIn(minWidth = WfSize.minTouchTarget, minHeight = WfSize.minTouchTarget),
                        enabled = !busy
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_chevron_right), contentDescription = null,
                            modifier = Modifier.size(20.dp).rotate(180f)
                        )
                        Text(stringResource(R.string.order_back_to_products))
                    }
                    ProductDetail(
                        product = selected.second,
                        collection = selected.first,
                        adding = busy,
                        onAdd = { line -> onAdd(line) { selectedId = null } },
                        forOrder = true
                    )
                }
            } else {
                val focus = LocalFocusManager.current
                val closePanel = {
                    focus.clearFocus()
                    scope.launch {
                        sheetState.hide()
                        if (!sheetState.isVisible) onDismiss()
                    }
                    Unit
                }
                val searchLabel = stringResource(R.string.order_search)
                Row(
                    Modifier.fillMaxWidth().padding(start = WfSpacing.md, end = WfSpacing.md, bottom = WfSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = scheme.surface, contentColor = scheme.primary,
                        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, scheme.primary)
                    ) {
                        IconButton(onClick = closePanel, enabled = !busy, modifier = Modifier.size(WfSize.minTouchTarget)) {
                            Icon(
                                painterResource(R.drawable.ic_chevron_right),
                                contentDescription = stringResource(R.string.auth_back),
                                modifier = Modifier.rotate(180f)
                            )
                        }
                    }
                    OutlinedTextField(
                        state = searchField,
                        modifier = Modifier.weight(1f)
                            .height(WfSize.minTouchTarget)
                            .semantics { contentDescription = searchLabel },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        placeholder = { Text(searchLabel) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = scheme.surface,
                            unfocusedContainerColor = scheme.surface,
                            focusedBorderColor = scheme.onPrimaryContainer,
                            unfocusedBorderColor = scheme.outlineVariant
                        ),
                        contentPadding = PaddingValues(horizontal = WfSpacing.lg, vertical = WfSpacing.sm),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { focus.clearFocus() }
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(topStart = WfRadius.xl, topEnd = WfRadius.xl),
                    color = scheme.surfaceContainerLow
                ) {
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(WfSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
                        ) {
                            if (collections.isNotEmpty()) item(key = "categories") {
                                if (activeCategory != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)) {
                                        ActiveProductCategory(activeCategory.name, onClear = { categoryId = null })
                                        HorizontalDivider()
                                    }
                                } else {
                                    ProductCategories(collections, onSelect = {
                                        focus.clearFocus()
                                        categoryId = it
                                    })
                                }
                            }
                            if (failed) item { Text(stringResource(R.string.cart_error), color = scheme.error) }
                            when {
                                catalog == null -> item { CircularProgressIndicator() }
                                catalog?.isFailure == true -> item {
                                    Text(stringResource(R.string.order_catalog_error))
                                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.auth_retry)) }
                                }
                                matching.isEmpty() -> item {
                                    Text(stringResource(R.string.order_search_empty))
                                    if (categoryId != null && query.isNotBlank()) {
                                        TextButton(onClick = { categoryId = null }) {
                                            Text(stringResource(R.string.order_search_all))
                                        }
                                    }
                                }
                                else -> matching.forEach { collection ->
                                    collection.sections.forEach { section ->
                                        item(key = "section/${collection.id}/${section.id}") {
                                            Text(
                                                section.name,
                                                modifier = Modifier.padding(top = WfSpacing.xs),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                        items(section.products, key = { it.id }) { product ->
                                            val openDetail = {
                                                focus.clearFocus()
                                                selectedId = product.id
                                            }
                                            OrderProductCard(
                                                product, quantities[product.name] ?: 0, enabled = !busy,
                                                onOpen = openDetail,
                                                onAdd = {
                                                    if (product.modifierGroups.isEmpty()) {
                                                        onAdd(OrderLine(product.name, 1, product.priceInCents)) {}
                                                    } else {
                                                        openDetail()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Surface(color = scheme.surfaceContainerLow) {
                            Button(
                                onClick = closePanel,
                                modifier = Modifier.fillMaxWidth().padding(WfSpacing.md)
                                    .height(WfSize.minTouchTarget),
                                enabled = !busy && lines.isNotEmpty(),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = WfThemeExtras.onAdvanceAction,
                                    disabledContainerColor = scheme.outlineVariant,
                                    disabledContentColor = scheme.onSurfaceVariant
                                )
                            ) {
                                Text(stringResource(R.string.order_continue_products))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveProductCategory(name: String, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val removeLabel = stringResource(R.string.order_remove_category, name)
    Surface(
        onClick = onClear,
        modifier = Modifier.semantics { contentDescription = removeLabel },
        shape = CircleShape,
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outline)
    ) {
        Row(
            modifier = Modifier.heightIn(min = WfSize.minTouchTarget).padding(horizontal = WfSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ProductCategories(
    collections: List<CartaCollection>,
    onSelect: (String?) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val categories = listOf(null to stringResource(R.string.order_all_categories)) +
        collections.map { it.id to it.name }
    Column(
        Modifier.fillMaxWidth().selectableGroup().padding(bottom = WfSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(WfSpacing.sm)
    ) {
        categories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                row.forEach { (id, name) ->
                    val selected = id == null
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = if (selected) scheme.primaryContainer else scheme.surface,
                        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            Modifier.heightIn(min = WfSize.minTouchTarget)
                                .selectable(selected, role = Role.Tab, onClick = { onSelect(id) })
                                .padding(horizontal = WfSpacing.xs, vertical = WfSpacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(WfSpacing.xs, Alignment.CenterVertically)
                        ) {
                            if (id == null) {
                                Icon(
                                    painterResource(R.drawable.ic_order_bag),
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp).padding(9.dp),
                                    tint = scheme.primary
                                )
                            } else {
                                CartaPhoto(
                                    "carta/categorias/$id.png", description = null,
                                    modifier = Modifier.size(44.dp), contentScale = ContentScale.Fit
                                )
                            }
                            Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderProductCard(
    product: CartaProduct,
    quantity: Long,
    enabled: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onOpen,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.fillMaxWidth().padding(WfSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(WfSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WfSpacing.xs)) {
                Text(product.name, style = MaterialTheme.typography.titleSmall)
                if (product.description.isNotBlank()) {
                    Text(
                        product.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (quantity > 0) {
                    Text(
                        stringResource(R.string.order_added_quantity, quantity),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary
                    )
                }
            }
            Text(product.price, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            FilledIconButton(
                onClick = onAdd, enabled = enabled,
                modifier = Modifier.size(WfSize.minTouchTarget),
                shape = MaterialTheme.shapes.medium,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = WfThemeExtras.purchaseAction,
                    contentColor = WfThemeExtras.onPurchaseAction,
                    disabledContainerColor = scheme.outlineVariant,
                    disabledContentColor = scheme.onSurfaceVariant
                )
            ) {
                Icon(
                    painterResource(R.drawable.ic_carta_add),
                    contentDescription = stringResource(R.string.order_add_named, product.name)
                )
            }
        }
    }
}
