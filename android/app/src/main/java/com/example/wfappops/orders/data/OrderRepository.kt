package com.example.wfappops.orders.data

import android.content.Context
import com.example.wfappops.auth.hasOpsAccess
import com.example.wfappops.carta.data.OrderCatalogRepository
import com.example.wfappops.carta.loadOrderCarta
import com.example.wfappops.orders.model.TableService
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal class OrderRepository(
    context: Context,
    private val storageId: String,
    private val localDraftEmployeeId: String? = null
) {
    private val appContext = context.applicationContext
    // Un espacio propio evita mezclar borradores personales con pedidos remotos o del prototipo.
    private val store = TableStore(appContext, localDraftEmployeeId?.let {
        "local-drafts-v1-${storageId.length}:$storageId-$it"
    } ?: storageId)
    private val catalog by lazy { OrderCatalogRepository(appContext) }
    val manual: Boolean get() = localDraftEmployeeId != null
    private val cachedTableCodes by lazy {
        val context = runCatching {
            com.example.wfappops.auth.data.SessionStore(appContext).read()?.verifiedBusiness?.context
        }.getOrNull()
        if (context == null || context.businessId != storageId || context.employeeId != localDraftEmployeeId ||
            "pos.tables.view" !in context.permissions) emptyMap()
        else {
            val cache = com.example.wfappops.tables.TablesCache(appContext)
            context.locations.flatMap { location ->
                val scope = org.json.JSONArray(listOf(storageId, context.employeeId, context.membershipId,
                    context.accessVersion, location.id)).toString()
                cache.peek(scope, location.id)?.areas.orEmpty().flatMap { it.tables }
                    .map { (location.id to it.id) to it.code }
            }.toMap()
        }
    }

    suspend fun tableLocations() = withContext(Dispatchers.IO) {
        val session = checkNotNull(com.example.wfappops.auth.data.SessionStore(appContext).read())
        val context = checkNotNull(session.verifiedBusiness).context
        check(appContext.hasOpsAccess(storageId) && context.businessId == storageId)
        check(context.employeeId == localDraftEmployeeId && "pos.tables.view" in context.permissions)
        context.locations
    }

    suspend fun loadTables(locationId: String, refresh: Boolean = false) = withContext(Dispatchers.IO) {
        val session = checkNotNull(com.example.wfappops.auth.data.SessionStore(appContext).read())
        check(appContext.hasOpsAccess(storageId))
        check(session.verifiedBusiness?.context?.employeeId == localDraftEmployeeId)
        com.example.wfappops.auth.data.AccountRepository(appContext).tables(session, storageId, locationId, refresh)
    }

    fun observeCatalog() = if (localDraftEmployeeId != null) {
        flow { emit(loadOrderCarta(appContext.assets)) }
    } else catalog.observe()

    fun <T> observe(select: (TableService) -> T) =
        store.changes.map { read(select) }.distinctUntilChanged()

    suspend fun <T> read(select: (TableService) -> T): T = withContext(Dispatchers.IO) {
        check(appContext.hasOpsAccess(storageId))
        if (localDraftEmployeeId != null) return@withContext select(store.update { service ->
            val updated = service.ensureDraftOrders().closeEmptyDraftOrders()
            updated.copy(visits = updated.visits.map { visit ->
                val code = if (visit.tableCode.isNullOrBlank()) cachedTableCodes[visit.locationId to visit.remoteTableId] else null
                if (code != null) visit.copy(tableCode = code) else visit
            })
        })
        // Los archivos del prototipo no son una fuente de pedidos del servidor.
        throw IOException("Order service unavailable")
    }

    suspend fun update(change: (TableService) -> TableService): TableService = withContext(Dispatchers.IO) {
        check(appContext.hasOpsAccess(storageId))
        if (localDraftEmployeeId != null) return@withContext store.update { change(it.ensureDraftOrders().closeEmptyDraftOrders()) }
        throw IOException("Order service unavailable")
    }
}
