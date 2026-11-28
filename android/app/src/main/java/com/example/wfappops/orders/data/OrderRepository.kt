package com.example.wfappops.orders.data

import android.content.Context
import com.example.wfappops.orders.domain.OrderGateway
import com.example.wfappops.orders.domain.OrderRefreshResult
import com.example.wfappops.orders.domain.TrackingWarning
import com.example.wfappops.orders.domain.refreshOrderSnapshot
import com.example.wfappops.orders.model.ClosureReadiness
import com.example.wfappops.orders.model.FulfillmentRequest
import com.example.wfappops.orders.model.FulfillmentAction
import com.example.wfappops.orders.model.isWaiterAction
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.auth.hasOpsAccess
import com.example.wfappops.orders.model.TableService
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.example.wfappops.auth.data.AuthApi
import com.example.wfappops.core.model.ApiException
import com.example.wfappops.auth.data.SessionStore
import com.example.wfappops.orders.model.OrderLine
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal class OrderRepository(
    context: Context,
    private val storageId: String,
    private val localDraftEmployeeId: String? = null
) : OrderGateway {
    private val appContext = context.applicationContext
    private val sessionAccess = OrderSessionAccess(appContext, storageId, localDraftEmployeeId)
    private val references = OrderReferenceDataSource(appContext, storageId, sessionAccess)
    // Un espacio propio evita mezclar borradores personales con pedidos remotos o del prototipo.
    private val store = TableStore(appContext, localDraftEmployeeId?.let {
        "local-drafts-v1-${storageId.length}:$storageId-$it"
    } ?: storageId)
    private val networkLock = networkLocks.getOrPut("$storageId/$localDraftEmployeeId") { Mutex() }
    private val fetched get() = fetchTimes

    override suspend fun permissions(): Set<String> = withContext(Dispatchers.IO) {
        val active = checkNotNull(SessionStore(appContext).read())
        check(active.verifiedBusiness?.context?.employeeId == localDraftEmployeeId && appContext.hasOpsAccess(storageId))
        checkNotNull(active.verifiedBusiness).context.permissions.toSet()
    }

    /** Solo un GET de resúmenes por local; no dispara un detalle por cada tarjeta. */
    override suspend fun refreshOrders(locationId: String, force: Boolean) = withContext(Dispatchers.IO) {
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
                Unit
            }
        }
    }

    override suspend fun refreshOrder(visitId: String): OrderRefreshResult = withContext(Dispatchers.IO) {
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            val id = visit.remoteOrderId ?: return@withLock OrderRefreshResult()
            val location = checkNotNull(visit.locationId)
            ordersAccess(location, setOf("pos.orders.view")) { api, token, name, verify ->
                refreshOrderSnapshot(
                    loadOrder = {
                        parseOpsOrder(api.ordersRequest(token, storageId, "ops/locations/$location/orders/$id"), location)
                            .also { require(it.id == id) }
                    },
                    saveOrder = { order ->
                        verify()
                        store.update { source -> source.applyRemoteOrderDetail(visitId, order, name) }
                    },
                    loadTracking = {
                        readAllPages { cursor -> parseFulfillmentUnits(
                            api.fulfillmentRequest(token, storageId, location, orderId = id, cursor = cursor), orderId = id) }
                    },
                    saveTracking = { delivery ->
                        verify()
                        store.update { it.mergeFulfillment(location, delivery) }
                    },
                    classifyFailure = { error ->
                        if (error is ApiException && error.status == 401) throw error
                        verify()
                        if (error is ApiException && error.status == 403) TrackingWarning.ACCESS_DENIED
                        else TrackingWarning.UNAVAILABLE
                    }
                )
            }
        }
    }

    override suspend fun send(visitId: String, quantities: Map<Int, Int>, expectedDraft: List<OrderLine>) = withContext(Dispatchers.IO) {
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            check(visit.pendingSend != null || visit.draft == expectedDraft) { "El borrador cambió. Revisa los productos antes de enviar." }
            check(visit.pendingCancellation == null) { "Confirma primero la anulación pendiente." }
            check(visit.pendingClose == null) { "Confirma primero el cierre pendiente." }
            check(visit.pendingPreparation == null) { "Confirma primero el cambio de preparación pendiente." }
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
                } catch (error: ApiException) {
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

    override suspend fun cancel(visitId: String, expectedVersion: Int, reason: String) = withContext(Dispatchers.IO) {
        require(reason.trim().length in 3..255)
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            check(visit.pendingClose == null) { "Confirma primero el cierre pendiente." }
            check(visit.pendingPreparation == null) { "Confirma primero el cambio de preparación pendiente." }
            check(visit.pendingSend == null)
            check(visit.pendingCancellation == null) { "Confirma primero la anulación pendiente." }
            ordersAccess(checkNotNull(visit.locationId), setOf("pos.orders.cancel")) { api, token, name, verify ->
                val path = "ops/locations/${visit.locationId}/orders/${checkNotNull(visit.remoteOrderId)}/cancel"
                val body = JSONObject().put("expected_version", expectedVersion).put("reason", reason.trim())
                try {
                    val order = parseOpsOrder(api.ordersRequest(token, storageId, path, "POST", body), checkNotNull(visit.locationId))
                    require(order.id == visit.remoteOrderId && order.status != "open")
                    verify()
                    store.update { it.mergeOrder(order, name) }
                    fetched.clear()
                } catch (error: ApiException) {
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

    override suspend fun cancelUnits(visitId: String, dispatchIndex: Int, indices: Set<Int>, reason: String, version: Int) = withContext(Dispatchers.IO) {
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            check(visit.pendingClose == null) { "Confirma primero el cierre pendiente." }
            check(visit.pendingPreparation == null) { "Confirma primero el cambio de preparación pendiente." }
            val location = checkNotNull(visit.locationId)
            val orderId = checkNotNull(visit.remoteOrderId)
            ordersAccess(location, setOf("pos.orders.cancel", "pos.orders.view")) { api, token, name, verify ->
                val displayed = presentation(TableService(listOf(visit))).visits.single()
                val captured = visit.pendingCancellation ?: captureUnitCancellation(displayed, dispatchIndex, indices, reason, version).also { body ->
                    store.update { source -> source.copy(visits = source.visits.map {
                        if (it.id != visitId) it else { check(it == visit); it.copy(pendingCancellation = body) }
                    }) }
                }
                val body = JSONObject(captured)
                val path = "ops/locations/$location/orders/$orderId"
                if (visit.pendingCancellation != null) {
                    val latest = parseOpsOrder(api.ordersRequest(token, storageId, path), location)
                    require(latest.id == orderId)
                    verify()
                    store.update { it.applyRemoteOrderDetail(visitId, latest, name) }
                    if (latest.confirmsCancellation(body)) return@ordersAccess
                }
                var accepted = false
                try {
                    val confirmedVersion = validateCancellationReceipt(api.ordersRequest(token, storageId, "$path/cancel-items", "POST", body), body, orderId)
                    accepted = true
                    verify()
                    fetched.clear()
                    val order = parseOpsOrder(api.ordersRequest(token, storageId, path), location)
                    require(order.id == orderId && order.version >= confirmedVersion && order.confirmsCancellation(body)) {
                        "La anulación fue recibida; falta verificar el detalle actualizado de los productos."
                    }
                    verify()
                    store.update { it.applyRemoteOrderDetail(visitId, order, name) }
                } catch (error: ApiException) {
                    // Un fallo de lectura posterior al POST conserva la decisión ya enviada para reconciliarla.
                    if (!accepted && error.status in setOf(400, 403, 404, 409, 413, 422) && error.code != "pos.client_id_reused") {
                        verify()
                        store.update { source -> source.copy(visits = source.visits.map {
                            if (it.id == visitId) it.copy(pendingCancellation = null) else it
                        }) }
                        if (error.code == "pos.stale_version") {
                            val order = parseOpsOrder(api.ordersRequest(token, storageId, path), location)
                            require(order.id == orderId)
                            verify()
                            store.update { it.mergeOrder(order, name) }
                        }
                    }
                    throw error
                }
            }
        }
    }

    override fun availableActions(visit: TableVisit, line: OrderLine): List<FulfillmentAction> = visit.waiterActions(line)

    override suspend fun prepare(visitId: String, requested: FulfillmentRequest?) = withContext(Dispatchers.IO) {
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            check(visit.pendingClose == null)
            val location = checkNotNull(visit.locationId)
            val orderId = checkNotNull(visit.remoteOrderId)
            ordersAccess(location, setOf("pos.orders.view")) { api, token, name, verify ->
                // Solo una captura nueva se valida contra el estado actual. Un reintento debe conservar la decisión original.
                val command = visit.pendingPreparation?.let(PreparationCommand::decode) ?: checkNotNull(requested).let { request ->
                    require(request.snapshot.id == visit.id && request.snapshot.remoteOrderId == visit.remoteOrderId)
                    capturePreparation(request.snapshot, request.line, request.action, request.reason, request.confirmed)
                }.also { command ->
                    require(command.action.isWaiterAction) { "La preparación se gestiona desde la estación." }
                    validatePreparation(visit, command)
                    verify()
                    store.update { source -> source.copy(visits = source.visits.map {
                        if (it.id != visitId) it else { check(it == visit); it.copy(pendingPreparation = command.encode()) }
                    }) }
                }
                require(command.orderId == orderId)
                val path = "ops/locations/$location/orders/$orderId"
                var accepted = false
                fun synchronize(minimumVersion: Int = 1, finish: Boolean) {
                    val order = parseOpsOrder(api.ordersRequest(token, storageId, path), location)
                    require(order.id == orderId && order.version >= minimumVersion)
                    verify()
                    store.update { source -> source.applyRemoteOrderDetail(visitId, order, name) }
                    val units = readAllPages { cursor -> parseFulfillmentUnits(api.fulfillmentRequest(token, storageId, location,
                        orderId = orderId, cursor = cursor), orderId = orderId) }
                    verify()
                    store.update { source -> source.mergeFulfillment(location, units).let { merged ->
                        if (!finish) merged else merged.copy(visits = merged.visits.map {
                            if (it.id == visitId) it.copy(pendingPreparation = null) else it
                        })
                    } }
                }
                if (!command.action.isWaiterAction) {
                    // Migración: reconciliar capturas antiguas con lecturas; nunca volver a escribir en cocina.
                    synchronize(finish = true)
                    return@ordersAccess
                }
                command.acknowledgedVersion?.let { version ->
                    // La decisión ya fue aceptada: los reintentos solo consultan el resultado.
                    synchronize(version, finish = true)
                    return@ordersAccess
                }
                try {
                    val body = JSONObject(command.body)
                    val receipt = api.ordersRequest(token, storageId, "$path/${command.action.route}", "POST", body)
                    val version = validateCancellationReceipt(receipt, body, orderId)
                    accepted = true
                    verify()
                    store.update { source -> source.copy(visits = source.visits.map {
                        if (it.id == visitId) it.copy(pendingPreparation = command.copy(acknowledgedVersion = version).encode()) else it
                    }) }
                    fetched.clear()
                    synchronize(version, finish = true)
                } catch (error: ApiException) {
                    if (!accepted && error.status in setOf(400, 403, 404, 409, 413, 422) && error.code != "pos.client_id_reused") {
                        verify()
                        store.update { source -> source.copy(visits = source.visits.map {
                            if (it.id == visitId) it.copy(pendingPreparation = null) else it
                        }) }
                        if (error.status == 409) synchronize(finish = false)
                    }
                    throw error
                }
            }
        }
    }

    override suspend fun closureReadiness(visitId: String): ClosureReadiness = withContext(Dispatchers.IO) {
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            ordersAccess(checkNotNull(visit.locationId), setOf("pos.orders.view")) { api, token, _, verify ->
                val id = checkNotNull(visit.remoteOrderId)
                parseClosureReadiness(api.ordersRequest(token, storageId, "ops/locations/${visit.locationId}/orders/$id/closure-readiness"), id)
                    .also { verify() }
            }
        }
    }

    override suspend fun closeAttention(visitId: String, readiness: ClosureReadiness?): Unit = withContext(Dispatchers.IO) {
        networkLock.withLock {
            val visit = store.read().visits.first { it.id == visitId }
            check(visit.pendingPreparation == null && visit.pendingCancellation == null && visit.pendingSend == null)
            val location = checkNotNull(visit.locationId)
            val id = checkNotNull(visit.remoteOrderId)
            ordersAccess(location, setOf("pos.orders.view")) { api, token, name, verify ->
                val capture = visit.pendingClose ?: checkNotNull(readiness).let { ready ->
                    require(!visit.closed && ready.orderId == id)
                    captureClosure(ready).also { body ->
                        verify()
                        store.update { source -> source.copy(visits = source.visits.map {
                            if (it.id != visitId) it else { check(it == visit); it.copy(pendingClose = body) }
                        }) }
                    }
                }
                val body = JSONObject(capture)
                val path = "ops/locations/$location/orders/$id"
                var accepted = false
                try {
                    val version = validateClosureReceipt(api.ordersRequest(token, storageId, "$path/close", "POST", body), body, id)
                    accepted = true
                    verify()
                    fetched.clear()
                    val order = parseOpsOrder(api.ordersRequest(token, storageId, path), location)
                    require(order.id == id && order.version.toLong() >= version && order.status == "closed")
                    verify()
                    store.update { source -> source.mergeOrder(order, name).let { merged -> merged.copy(visits = merged.visits.map {
                        if (it.id == visitId) it.copy(pendingClose = null) else it
                    }) } }
                } catch (error: ApiException) {
                    if (!accepted && error.status in setOf(400, 403, 404, 409, 413, 422) && error.code != "pos.client_id_reused") {
                        verify()
                        store.update { source -> source.copy(visits = source.visits.map { if (it.id == visitId) it.copy(pendingClose = null) else it }) }
                        if (error.status == 409) {
                            val order = parseOpsOrder(api.ordersRequest(token, storageId, path), location)
                            require(order.id == id); verify()
                            store.update { it.mergeOrder(order, name) }
                        }
                    }
                    throw error
                }
            }
        }
    }

    private suspend fun <T> ordersAccess(locationId: String, required: Set<String>,
        action: suspend (AuthApi, String, String, () -> Unit) -> T): T =
        sessionAccess.authorized(locationId, required, action)

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
            SessionStore(appContext).read()?.verifiedBusiness?.context
        }.getOrNull()
        if (context == null || context.businessId != storageId || context.employeeId != localDraftEmployeeId ||
            "pos.tables.view" !in context.permissions) emptyMap()
        else {
            val cache = com.example.wfappops.tables.TablesCache(appContext)
            context.locations.flatMap { location ->
                val scope = JSONArray(listOf(storageId, context.employeeId, context.membershipId,
                    context.accessVersion, location.id)).toString()
                cache.peek(scope, location.id)?.areas.orEmpty().flatMap { it.tables }
                    .map { (location.id to it.id) to it.code }
            }.toMap()
        }
    }

    override suspend fun tableLocations() = references.tableLocations()
    override suspend fun loadTables(locationId: String, refresh: Boolean) = references.tables(locationId, refresh)
    override suspend fun menuLocations() = references.menuLocations()
    override suspend fun loadMenu(locationId: String, refresh: Boolean) = references.menu(locationId, refresh)

    override fun <T> observe(select: (TableService) -> T) =
        store.changes.map { read(select) }.distinctUntilChanged()

    override suspend fun <T> read(select: (TableService) -> T): T = withContext(Dispatchers.IO) {
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

    override suspend fun update(change: (TableService) -> TableService): TableService = withContext(Dispatchers.IO) {
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

    private fun presentation(service: TableService) =
        service.withRemoteFulfillment()

    private companion object {
        val networkLocks = ConcurrentHashMap<String, Mutex>()
        val fetchTimes = ConcurrentHashMap<String, Long>()
    }
}

