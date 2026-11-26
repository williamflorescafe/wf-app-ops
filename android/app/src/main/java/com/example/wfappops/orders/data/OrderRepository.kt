package com.example.wfappops.orders.data

import android.content.Context
import com.example.wfappops.auth.hasOpsAccess
import com.example.wfappops.orders.model.TableService
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.example.wfappops.auth.data.AuthApi
import com.example.wfappops.auth.data.AuthApiException
import com.example.wfappops.auth.data.SessionStore
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.model.withLocalKitchenStatuses
import com.example.wfappops.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

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
    val manual: Boolean get() = localDraftEmployeeId != null
    private val networkLock = networkLocks.getOrPut("$storageId/$localDraftEmployeeId") { Mutex() }
    private val fetched get() = fetchTimes

    suspend fun permissions(): Set<String> = withContext(Dispatchers.IO) {
        val active = checkNotNull(SessionStore(appContext).read())
        check(active.verifiedBusiness?.context?.employeeId == localDraftEmployeeId && appContext.hasOpsAccess(storageId))
        checkNotNull(active.verifiedBusiness).context.permissions.toSet()
    }

    /** Solo un GET de resúmenes por local; no dispara un detalle por cada tarjeta. */
    suspend fun refreshOrders(locationId: String, force: Boolean = false) = withContext(Dispatchers.IO) {
        networkLock.withLock {
            ordersAccess(locationId, setOf("pos.orders.view")) { api, token, locationName, verify ->
                val stamp = android.os.SystemClock.elapsedRealtime()
                val context = checkNotNull(SessionStore(appContext).read()?.verifiedBusiness).context
                val cacheKey = "$storageId/$localDraftEmployeeId/${context.membershipId}/${context.accessVersion}/${token.hashCode()}/$locationId"
                if (force || fetched[cacheKey]?.let { stamp - it !in 0 until 5_000 } != false) {
                    val orders = parseOpsOrders(api.ordersRequest(token, storageId, "ops/locations/$locationId/orders"), locationId)
                    verify()
                    store.update { source ->
                        var merged = normalize(source)
                        orders.forEach { merged = merged.mergeOrder(it, locationName) }
                        val ids = orders.map { it.id }.toSet()
                        merged.copy(visits = merged.visits.map { visit ->
                            if (visit.locationId == locationId && visit.remoteOrderId != null && visit.remoteOrderId !in ids && !visit.closed)
                                visit.copy(remoteStatus = "unlisted", closed = true) else visit
                        })
                    }
                    fetched[cacheKey] = stamp
                }
            }
        }
    }

    suspend fun refreshOrder(visitId: String) = withContext(Dispatchers.IO) {
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            val id = visit.remoteOrderId ?: return@withLock
            ordersAccess(checkNotNull(visit.locationId), setOf("pos.orders.view")) { api, token, name, verify ->
                val order = parseOpsOrder(api.ordersRequest(token, storageId, "ops/locations/${visit.locationId}/orders/$id"), checkNotNull(visit.locationId))
                require(order.id == id)
                verify()
                store.update { source ->
                    val pending = source.visits.first { it.id == visitId }.pendingSend?.let(PendingOrderSend::decode)
                    if (pending != null && order.rounds?.any { it.clientRoundId == pending.roundId } == true)
                        source.acknowledgeSend(visitId, pending, order, name) else source.mergeOrder(order, name)
                }
            }
        }
    }

    suspend fun send(visitId: String, quantities: Map<Int, Int>, expectedDraft: List<OrderLine>) = withContext(Dispatchers.IO) {
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            check(visit.pendingSend != null || visit.draft == expectedDraft) { "El borrador cambió. Revisa los productos antes de enviar." }
            val previous = visit.pendingSend?.let(PendingOrderSend::decode)
            val isAddition = previous?.path?.endsWith("/rounds") ?: (visit.remoteOrderId != null)
            val permissions = setOf(if (isAddition) "pos.orders.update" else "pos.orders.create", "pos.orders.send_to_kitchen")
            ordersAccess(checkNotNull(visit.locationId), permissions) { api, token, name, verify ->
                val pending = previous ?: captureOrderSend(visit, quantities).also { capture ->
                    store.update { service -> service.copy(visits = service.visits.map {
                        if (it.id != visitId) it else { check(it == visit); it.copy(pendingSend = capture.encode()) }
                    }) }
                }
                try {
                    val order = parseOpsOrder(api.ordersRequest(token, storageId, pending.path, "POST", JSONObject(pending.body)), checkNotNull(visit.locationId))
                    verify()
                    store.update { it.acknowledgeSend(visitId, pending, order, name) }
                    fetched.clear()
                } catch (error: AuthApiException) {
                    // Rechazo definitivo: se permite corregir el borrador. Un resultado incierto conserva la captura.
                    if (error.status in setOf(400, 403, 404, 409, 413, 422, 429) && error.code != "pos.client_id_reused") {
                        verify()
                        store.update { service -> service.copy(visits = service.visits.map {
                            if (it.id == visitId) it.copy(pendingSend = null) else it
                        }) }
                    }
                    throw error
                }
            }
        }
    }

    suspend fun cancel(visitId: String, expectedVersion: Int, reason: String) = withContext(Dispatchers.IO) {
        require(reason.trim().length in 3..255)
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            check(visit.pendingSend == null)
            ordersAccess(checkNotNull(visit.locationId), setOf("pos.orders.cancel")) { api, token, name, verify ->
                val path = "ops/locations/${visit.locationId}/orders/${checkNotNull(visit.remoteOrderId)}/cancel"
                val body = JSONObject().put("expected_version", expectedVersion).put("reason", reason.trim())
                try {
                    val order = parseOpsOrder(api.ordersRequest(token, storageId, path, "POST", body), checkNotNull(visit.locationId))
                    require(order.id == visit.remoteOrderId && order.status != "open")
                    verify()
                    store.update { it.mergeOrder(order, name) }
                    fetched.clear()
                } catch (error: AuthApiException) {
                    if (error.code == "pos.stale_version") {
                        val fresh = parseOpsOrder(api.ordersRequest(token, storageId, "ops/locations/${visit.locationId}/orders/${visit.remoteOrderId}"), checkNotNull(visit.locationId))
                        require(fresh.id == visit.remoteOrderId)
                        verify()
                        store.update { it.mergeOrder(fresh, name) }
                    }
                    throw error
                }
            }
        }
    }

    private suspend fun <T> ordersAccess(locationId: String, required: Set<String>, action: (AuthApi, String, String, () -> Unit) -> T): T {
        val sessions = SessionStore(appContext)
        val active = checkNotNull(sessions.read())
        val context = checkNotNull(active.verifiedBusiness).context
        check(appContext.hasOpsAccess(storageId) && active.businessId == storageId && context.businessId == storageId && context.employeeId == localDraftEmployeeId)
        if (!context.permissions.containsAll(required)) throw AuthApiException(403)
        val location = checkNotNull(context.locations.firstOrNull { it.id == locationId })
        try {
            return action(AuthApi(appContext), active.token, location.name) {
                val current = sessions.read()
                check(current?.token == active.token && current.verifiedBusiness?.context == context && appContext.hasOpsAccess(storageId))
            }
        } catch (error: AuthApiException) {
            if (error.status == 401 && sessions.read()?.token == active.token) {
                com.example.wfappops.auth.ActiveOpsAccess.current = null
                com.example.wfappops.auth.data.AccountRepository(appContext).clearSession()
            }
            throw error
        }
    }

    private fun normalize(service: TableService) = service.copy(visits = service.visits.map { visit ->
        // Las numeraciones y envíos del prototipo nunca se presentan como registros oficiales.
        if (visit.apiBacked) visit else visit.copy(apiBacked = true, orderNumber = null, numberReserved = false,
            dispatches = emptyList(), cashierHandoff = null, closed = visit.closed && visit.draft.isEmpty())
    })

    private fun visible(service: TableService): TableService {
        val context = checkNotNull(SessionStore(appContext).read()?.verifiedBusiness).context
        check(context.businessId == storageId && context.employeeId == localDraftEmployeeId && appContext.hasOpsAccess(storageId))
        val locations = context.locations.map { it.id }.toSet()
        return service.copy(visits = service.visits.filter { visit ->
            (visit.locationId == null || visit.locationId in locations) && (visit.remoteOrderId == null || "pos.orders.view" in context.permissions)
        })
    }
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

    suspend fun menuLocations() = withContext(Dispatchers.IO) {
        val session = checkNotNull(com.example.wfappops.auth.data.SessionStore(appContext).read())
        val context = checkNotNull(session.verifiedBusiness).context
        check(appContext.hasOpsAccess(storageId) && context.businessId == storageId && context.employeeId == localDraftEmployeeId)
        // La autorización de Carta se revalida en AccountRepository.menu; aquí solo elegimos el local.
        context.locations
    }

    suspend fun loadMenu(locationId: String, refresh: Boolean = false) = withContext(Dispatchers.IO) {
        val sessionStore = com.example.wfappops.auth.data.SessionStore(appContext)
        val session = checkNotNull(sessionStore.read())
        check(appContext.hasOpsAccess(storageId) && session.verifiedBusiness?.context?.employeeId == localDraftEmployeeId)
        try {
            com.example.wfappops.auth.data.AccountRepository(appContext).menu(session, storageId, locationId, refresh)
        } catch (error: com.example.wfappops.auth.data.AuthApiException) {
            if (error.status == 401 && sessionStore.read()?.token == session.token) {
                com.example.wfappops.auth.ActiveOpsAccess.current = null
                com.example.wfappops.auth.data.AccountRepository(appContext).clearSession()
            }
            throw error
        }
    }

    fun <T> observe(select: (TableService) -> T) =
        store.changes.map { read(select) }.distinctUntilChanged()

    suspend fun <T> read(select: (TableService) -> T): T = withContext(Dispatchers.IO) {
        check(appContext.hasOpsAccess(storageId))
        visible(TableService())
        if (localDraftEmployeeId != null) return@withContext select(presentation(visible(store.update { service ->
            val updated = normalize(service)
            updated.copy(visits = updated.visits.map { visit ->
                val code = if (visit.tableCode.isNullOrBlank()) cachedTableCodes[visit.locationId to visit.remoteTableId] else null
                if (code != null) visit.copy(tableCode = code) else visit
            })
        })))
        // Los archivos del prototipo no son una fuente de pedidos del servidor.
        throw IOException("Order service unavailable")
    }

    suspend fun update(change: (TableService) -> TableService): TableService = withContext(Dispatchers.IO) {
        check(appContext.hasOpsAccess(storageId))
        if (localDraftEmployeeId != null) return@withContext presentation(visible(store.update { stored ->
            val current = normalize(stored)
            val permittedIds = visible(current).visits.map { it.id }.toSet()
            val updated = change(current)
            current.visits.filter { it.id !in permittedIds }.forEach { hidden -> check(updated.visits.firstOrNull { it.id == hidden.id } == hidden) }
            current.visits.filter { it.pendingSend != null }.forEach { frozen ->
                val next = updated.visits.first { it.id == frozen.id }
                check(next.draft == frozen.draft && next.locationId == frozen.locationId && next.remoteTableId == frozen.remoteTableId && next.comment == frozen.comment)
            }
            current.visits.filter { it.remoteOrderId != null }.forEach { confirmed ->
                val next = updated.visits.first { it.id == confirmed.id }
                check(next.remoteOrderId == confirmed.remoteOrderId && next.orderNumber == confirmed.orderNumber &&
                    next.closed == confirmed.closed && next.dispatches == confirmed.dispatches &&
                    next.remoteTableId == confirmed.remoteTableId && next.locationId == confirmed.locationId)
            }
            updated
        }))
        throw IOException("Order service unavailable")
    }

    private fun presentation(service: TableService) = if (BuildConfig.DEBUG) service.withLocalKitchenStatuses() else service

    private companion object {
        val networkLocks = ConcurrentHashMap<String, Mutex>()
        val fetchTimes = ConcurrentHashMap<String, Long>()
    }
}
