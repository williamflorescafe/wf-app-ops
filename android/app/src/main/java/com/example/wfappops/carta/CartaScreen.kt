package com.example.wfappops.carta

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wfappops.R
import com.example.wfappops.carta.data.CatalogPhotoStore
import com.example.wfappops.carta.data.catalogHttpsUrl
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.ui.theme.WfThemeExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun CartaScreen(
    onExit: () -> Unit,
    onAdd: (OrderLine) -> Unit,
    adding: Boolean
) {
    val assets = LocalContext.current.assets
    val catalog by produceState<Result<List<CartaCollection>>?>(null, assets) {
        value = loadCarta(assets)
    }
    var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var sectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var productId by rememberSaveable { mutableStateOf<String?>(null) }

    val collections = catalog?.getOrNull().orEmpty()
    val collection = collections.find { it.id == collectionId }
    val product = remember(collection, productId) {
        collection?.sections?.asSequence()?.flatMap { it.products.asSequence() }
            ?.firstOrNull { it.id == productId }
    }
    val productGridState = key(collectionId, sectionId) { rememberLazyGridState() }
    val filterScrollState = key(collectionId) { rememberScrollState() }
    val goBack: () -> Unit = {
        if (productId != null) {
            productId = null
        } else if (collectionId != null) {
            collectionId = null
            sectionId = null
        } else {
            onExit()
        }
    }
    BackHandler(onBack = goBack)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (product == null) WfThemeExtras.cartaCanvas else MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                when {
                    catalog == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    catalog?.isFailure == true -> Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se pudo cargar la carta.")
                    }
                    product != null -> key(product.id) {
                        ProductDetail(product, collection?.name.orEmpty(), adding, onAdd)
                    }
                    collection != null -> ProductGrid(
                        title = collection.name,
                        sections = collection.sections,
                        activeSection = sectionId,
                        gridState = productGridState,
                        filterScrollState = filterScrollState,
                        onSectionChange = { sectionId = it },
                        onProductClick = { productId = it }
                    )
                    else -> Collections(collections) { collectionId = it }
                }
            }
        }
    }
}

@Composable
private fun Collections(collections: List<CartaCollection>, onSelect: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "Explora nuestra carta",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                collections.forEach { collection ->
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(0.8f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(collection.id) }
                    ) {
                        CartaPhoto(collection.photo, null, Modifier.matchParentSize())
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))
                            )
                        )
                        Text(
                            collection.name,
                            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductGrid(
    title: String,
    sections: List<CartaSection>,
    activeSection: String?,
    gridState: LazyGridState,
    filterScrollState: ScrollState,
    onSectionChange: (String?) -> Unit,
    onProductClick: (String) -> Unit
) {
    val products = remember(sections, activeSection) {
        sections.filter { activeSection == null || it.id == activeSection }.flatMap { it.products }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            title,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        if (sections.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(filterScrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeSection == null,
                    onClick = { onSectionChange(null) },
                    label = { Text("Todas") }
                )
                sections.forEach { section ->
                    FilterChip(
                        selected = activeSection == section.id,
                        onClick = { onSectionChange(section.id) },
                        label = { Text(section.name) }
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (products.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Aún no hay productos en esta sección.")
                }
            }
            items(products, key = { it.id }) { product ->
                ProductCard(product, onClick = { onProductClick(product.id) })
            }
        }
    }
}

@Composable
private fun ProductCard(product: CartaProduct, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            CartaPhoto(
                product.photos.firstOrNull(),
                null,
                Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    product.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(product.price, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun CartaPhoto(
    path: String?,
    description: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current.applicationContext
    val bitmap = key(path, context) {
        val photo by produceState<ImageBitmap?>(null) {
            if (path != null) {
                val url = catalogHttpsUrl(path)
                value = if (url != null) {
                    CatalogPhotoStore.load(context, url)?.asImageBitmap()
                } else withContext(Dispatchers.IO) {
                    try {
                        context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    } catch (_: IOException) {
                        null
                    }
                }
            }
        }
        photo
    }
    Box(modifier) {
        if (bitmap == null) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primaryContainer))
        } else {
            Image(bitmap, description, Modifier.matchParentSize(), contentScale = contentScale)
        }
    }
}
