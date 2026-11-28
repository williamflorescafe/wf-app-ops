package com.example.wfappops.orders.viewmodel

import com.example.wfappops.core.model.ApiException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wfappops.carta.CartaCollection
import com.example.wfappops.carta.validateOrderLine
import com.example.wfappops.orders.domain.OrderGateway
import com.example.wfappops.orders.domain.canSendToKitchen
import com.example.wfappops.orders.model.FulfillmentAction
import com.example.wfappops.orders.model.FulfillmentRequest
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.tables.localTableAttentions
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OrderViewModel(
    private val repository: OrderGateway,
    private val worker: TableWorker,
    private val savedState: SavedStateHandle,
    initialTable: Int?,
    initialVisitId: String?,
    initialLocationId: String? = null,
    initialRemoteTableId: String? = null,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {
    private val operationMutex = Mutex()
    private var grantedPermissions = emptySet<String>()
    private val mutableUiState = MutableStateFlow(
        OrderUiState(
            table = savedState.get<Int>(TABLE) ?: initialTable,
            canUpdateLines = false,
            canSimulateLineStatus = false,
            workerName = worker.name,
            openingSelectedTable = initialRemoteTableId != null && savedState.get<Int>(TABLE) == null &&
                initialTable == null && savedState.get<Boolean>(REMOTE_TABLE_OPENED) != true
        )
    )
    val uiState = mutableUiState.asStateFlow()
    private val catalog = MutableStateFlow<Result<List<CartaCollection>>?>(null)
    val catalogState = catalog.asStateFlow()
    private var catalogRequest: Job? = null
    private var tablesRequest: Job? = null
    private var visitId: String? = savedState.get<String>(VISIT_ID) ?: initialVisitId
    private val preferredMenuLocation = initialLocationId
    private var catalogLocationId: String? = null
    private var catalogCheckedAt = 0L
    private var menuRequestVersion = 0

    init {
        reload()
        if (initialTable == null || uiState.value.table == null) loadTables(initialLocationId)
        if (initialRemoteTableId != null && savedState.get<Boolean>(REMOTE_TABLE_OPENED) != true) {
            viewModelScope.launch {
                val ready = uiState.first { !it.busy && !it.tablesLoading && it.apiTables != null }
                val layout = ready.apiTables?.getOrNull()
                if (ready.visit?.remoteTableId == null && layout != null && layout.location.id == initialLocationId &&
                    layout.areas.any { area -> area.tables.any { it.id == initialRemoteTableId } }) {
                    savedState[REMOTE_TABLE_OPENED] = true
                    openApiTable(initialRemoteTableId)
                } else {
                    mutableUiState.update { it.copy(openingSelectedTable = false) }
                }
            }
        }
    }

    fun reload() = runOperation(blocking = uiState.value.visit == null, errorSource = OrderErrorSource.REFRESH) { current ->
        val permissions = repository.permissions()
        grantedPermissions = permissions
        val source = current.copy(canCancelOrder = "pos.orders.cancel" in permissions,
            canUpdateLines = "pos.orders.cancel" in permissions && "pos.orders.view" in permissions,
            canSend = canSendToKitchen(permissions, current.visit))
        // Access is already known; a subsequent delivery-state failure must not discard it.
        mutableUiState.update { it.copy(
            canCancelOrder = source.canCancelOrder,
            canUpdateLines = source.canUpdateLines,
            canSend = source.canSend
        ) }
        if (current.table == null && visitId == null) {
            val service = repository.update { it.createOrder(worker, apiBacked = true) }
            val visit = service.visits.last()
            visitId = visit.id
            savedState[TABLE] = visit.table
            savedState[VISIT_ID] = visit.id
            source.copy(table = visit.table).withVisits(service, visit.id)
        } else {
            val refresh = visitId?.let { id ->
                repository.read { it.visits.firstOrNull { visit -> visit.id == id } }?.remoteOrderId
                    ?.let { repository.refreshOrder(id) }
            }
            repository.read { source.copy(trackingWarning = refresh?.trackingWarning).withVisits(it, visitId) }
        }
    }

    fun loadCatalog() = loadMenu(false)
    fun refreshCatalog() = loadMenu(true)

    fun selectMenuLocation(locationId: String) {
        if (uiState.value.busy || uiState.value.visit?.hasRecordedProducts == true || uiState.value.visit?.remoteTableId != null) return
        savedState[MENU_LOCATION] = locationId
        loadMenu(true, locationId)
    }

    private fun loadMenu(refresh: Boolean, requestedLocation: String? = null) {
        val requested = requestedLocation ?: uiState.value.visit?.locationId ?: savedState.get<String>(MENU_LOCATION)
            ?: preferredMenuLocation ?: uiState.value.menuLocationId
        if (catalogRequest?.isActive == true && (requested == null || requested == catalogLocationId)) return
        if (!refresh && requested == catalogLocationId && catalog.value?.isSuccess == true &&
            elapsedMillis() - catalogCheckedAt in 0 until 60_000) return
        catalogRequest?.cancel()
        val requestVersion = ++menuRequestVersion
        if (requested != catalogLocationId || catalog.value?.isFailure == true) catalog.value = null
        catalogLocationId = requested
        catalogRequest = viewModelScope.launch {
            mutableUiState.update { it.copy(menuLoading = true) }
            try {
                val locations = repository.menuLocations()
                val selected = locations.firstOrNull { it.id == requested } ?: if (requested == null) locations.firstOrNull() else null
                checkNotNull(selected)
                catalogLocationId = selected.id
                mutableUiState.update { it.copy(menuLocations = locations, menuLocationId = selected.id) }
                val menu = repository.loadMenu(selected.id, refresh)
                if (requestVersion == menuRequestVersion) {
                    catalogCheckedAt = elapsedMillis()
                    catalog.value = Result.success(menu.collections)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestVersion == menuRequestVersion) {
                    catalog.value = Result.failure(error)
                    if ((error as? com.example.wfappops.core.model.ApiException)?.status == 401) {
                        mutableUiState.update { it.copy(sessionExpired = true) }
                    }
                }
            } finally {
                if (requestVersion == menuRequestVersion) mutableUiState.update { it.copy(menuLoading = false) }
            }
        }
    }

    fun openTable(table: Int) = runOperation { current -> openTable(current, table) }

    private suspend fun openTable(current: OrderUiState, table: Int): OrderUiState {
        val service = repository.read { it }
        val visit = checkNotNull(service.openVisit(table))
        visitId = visit.id
        savedState[TABLE] = table
        savedState[VISIT_ID] = visit.id
        return current.copy(table = table).withVisits(service, visit.id)
    }

    fun loadTables(locationId: String? = null, refresh: Boolean = false) {
        val current = uiState.value
        val sameLocation = locationId == null || locationId == current.selectedLocationId
        if (tablesRequest?.isActive == true && sameLocation) return
        tablesRequest?.cancel()
        tablesRequest = viewModelScope.launch {
            mutableUiState.update { it.copy(tablesLoading = true, tablesUserRefreshing = refresh) }
            try {
                val locations = repository.tableLocations()
                val selected = locations.firstOrNull { it.id == (locationId ?: uiState.value.visit?.locationId ?: uiState.value.menuLocationId ?: uiState.value.selectedLocationId) }
                    ?: locations.firstOrNull()
                mutableUiState.update {
                    it.copy(
                        locations = locations, selectedLocationId = selected?.id,
                        apiTables = current.apiTables.takeIf { cached -> selected?.id == current.selectedLocationId && cached?.isSuccess == true }
                    )
                }
                val layout = selected?.let { repository.loadTables(it.id, refresh) }
                selected?.let { repository.refreshOrders(it.id, refresh) }
                val attentions = repository.read { it.localTableAttentions() }
                mutableUiState.update { it.copy(apiTables = layout?.let(Result.Companion::success), tablesLoading = false, tablesUserRefreshing = false, attentions = attentions) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update { it.copy(apiTables = Result.failure(error), tablesLoading = false, tablesUserRefreshing = false) }
            }
        }
    }

    fun openApiTable(tableId: String) = runOperation { current ->
        check(!current.tablesLoading)
        val layout = checkNotNull(current.apiTables?.getOrNull())
        val selected = layout.areas.flatMap { it.tables }.first { it.id == tableId }
        repository.refreshOrders(layout.location.id)
        val occupied = repository.read { service -> service.visits.firstOrNull {
            !it.closed && it.locationId == layout.location.id && it.remoteTableId == tableId && it.remoteOrderId != null
        } }
        if (occupied != null) {
            check(current.visit?.draft.isNullOrEmpty()) { "La mesa tiene una comanda abierta. Conserva tu borrador y abre esa atención para agregar productos." }
            repository.refreshOrder(occupied.id)
            visitId = occupied.id
            savedState[TABLE] = occupied.table
            savedState[VISIT_ID] = occupied.id
            return@runOperation repository.read { current.copy(table = occupied.table).withVisits(it, occupied.id) }
        }
        val service = repository.update {
            val currentVisit = current.visit
            if (currentVisit != null) it.assignApiTable(currentVisit.id, layout.location, selected)
            else it.openApiTable(layout.location, selected, worker)
        }
        val visit = service.visits.first { !it.closed && it.locationId == layout.location.id && it.remoteTableId == tableId }
        visitId = visit.id
        savedState[TABLE] = visit.table
        savedState[VISIT_ID] = visit.id
        current.copy(table = visit.table).withVisits(service, visit.id)
    }

    fun addProduct(line: OrderLine) {
        val visit = uiState.value.visit?.takeUnless { it.closed } ?: return
        if (uiState.value.menuLoading) return
        runOperation(blocking = false, errorSource = OrderErrorSource.ADD_PRODUCT) { current ->
            val menu = repository.loadMenu(checkNotNull(line.locationId))
            val confirmed = try { menu.validateOrderLine(line) } catch (error: IllegalArgumentException) {
                catalog.value = Result.success(menu.collections)
                mutableUiState.update { it.copy(productSelectionChanged = true) }
                throw error
            } catch (error: IllegalStateException) {
                catalog.value = Result.success(menu.collections)
                mutableUiState.update { it.copy(productSelectionChanged = true) }
                throw error
            }
            val service = repository.update { stored ->
                val active = checkNotNull(stored.openVisit(visit.table))
                check(active.id == visit.id)
                require((active.draft + active.dispatches.flatMap { it.lines }).all { it.currencyCode == confirmed.currencyCode })
                stored.bindMenuLocation(visit.id, menu.location).add(visit.id, confirmed, Instant.now(), worker)
            }
            current.withVisits(service, visit.id).copy(addedProducts = current.addedProducts + 1)
        }
    }

    fun changeQuantity(line: OrderLine, delta: Int) = editVisit(
        change = { service, visit -> service.adjustDraft(visit.id, line, delta) }
    )

    fun saveNote(line: OrderLine, note: String) = editVisit(
        errorSource = OrderErrorSource.DRAFT_NOTE,
        change = { service, visit -> service.editDraftNote(visit.id, line, note.trim().ifEmpty { null }) },
        onSaved = { it.copy(savedNotes = it.savedNotes + 1) }
    )

    fun saveComment(comment: String) = editVisit(
        errorSource = OrderErrorSource.DRAFT_COMMENT,
        change = { service, visit -> service.saveComment(visit.id, comment) },
        onSaved = { it.copy(savedComments = it.savedComments + 1) }
    )

    fun sendToCashier() {
        mutableUiState.update { it.copy(operationMessage = "El envío a caja todavía no tiene un endpoint integrado.", storageError = true) }
    }

    fun sendOrder() {
        val current = uiState.value
        sendSelected(current.visit?.draft.orEmpty().mapIndexed { index, line -> index to line.quantity }.toMap())
    }

    fun sendSelected(quantities: Map<Int, Int>) {
        val visit = uiState.value.visit?.takeUnless { it.closed } ?: return
        if (uiState.value.busy || !uiState.value.canSend) return
        runOperation { current ->
            repository.send(visit.id, quantities, visit.draft)
            repository.read { current.withVisits(it, visit.id) }
        }
    }

    fun cancelOrder(expectedVersion: Int, reason: String) {
        val visit = uiState.value.visit ?: return
        runOperation { current ->
            repository.cancel(visit.id, expectedVersion, reason)
            repository.read { current.withVisits(it, visit.id) }
        }
    }

    fun refreshRemote() { if (uiState.value.visit?.remoteOrderId != null && !uiState.value.busy) reload() }

    fun recoverDraft() {
        val previous = uiState.value.visit?.takeIf { it.closed && it.draft.isNotEmpty() && it.pendingSend == null } ?: return
        runOperation { current ->
            val service = repository.update { source ->
                val saved = source.visits.first { it.id == previous.id }
                check(saved.draft == previous.draft)
                val created = source.createOrder(worker, apiBacked = true)
                val next = created.visits.last()
                created.copy(visits = created.visits.map {
                    when (it.id) {
                        saved.id -> it.copy(draft = emptyList())
                        next.id -> it.copy(draft = saved.draft, locationId = saved.locationId,
                            locationName = saved.locationName, comment = saved.comment)
                        else -> it
                    }
                })
            }
            val next = service.visits.last()
            visitId = next.id; savedState[VISIT_ID] = next.id; savedState[TABLE] = next.table
            current.copy(table = next.table).withVisits(service, next.id)
        }
    }

    fun dismissKitchenUnavailable() = mutableUiState.update { it.copy(kitchenUnavailable = false) }

    fun advanceLine(dispatchIndex: Int, lineIndex: Int, expected: KitchenItemStatus) {
        val visit = uiState.value.visit?.takeUnless { it.closed } ?: return
        val line = visit.dispatches.getOrNull(dispatchIndex)?.lines?.getOrNull(lineIndex) ?: return
        if (line.dispatchStatus != expected) return
        if (visit.remoteFulfillment.isNotEmpty()) {
            if (line.dispatchStatus != KitchenItemStatus.READY || FulfillmentAction.DELIVER !in repository.availableActions(visit, line)) return
            val request = FulfillmentRequest(visit, line, FulfillmentAction.DELIVER)
            runOperation { current ->
                repository.prepare(visit.id, request)
                repository.read { current.withVisits(it, visit.id) }
            }
            return
        }

    }

    fun cancelLines(dispatchIndex: Int, lineIndices: Set<Int>, reason: String, version: Int) {
        val visit = uiState.value.visit ?: return
        if (!uiState.value.canUpdateLines) return
        runOperation { current ->
            repository.cancelUnits(visit.id, dispatchIndex, lineIndices, reason, version)
            repository.read { current.withVisits(it, visit.id) }
        }
    }

    fun retryPreparation() {
        val visit = uiState.value.visit?.takeIf { it.pendingPreparation != null } ?: return
        runOperation { current ->
            repository.prepare(visit.id, null)
            repository.read { current.withVisits(it, visit.id) }
        }
    }

    fun availableActions(visit: TableVisit, line: OrderLine) = repository.availableActions(visit, line)

    fun operateCaptured(request: FulfillmentRequest) {
        val visit = uiState.value.visit?.takeIf { it.id == request.snapshot.id && it.remoteOrderId == request.snapshot.remoteOrderId } ?: return
        runOperation { current ->
            repository.prepare(visit.id, request)
            repository.read { current.withVisits(it, visit.id) }
        }
    }

    fun checkClosure() {
        val visit = uiState.value.visit?.takeIf { it.remoteOrderId != null && !it.closed } ?: return
        runOperation { current -> current.copy(closureReadiness = repository.closureReadiness(visit.id)) }
    }

    fun dismissClosure() = mutableUiState.update { it.copy(closureReadiness = null) }

    fun closeAttention() {
        val visit = uiState.value.visit ?: return
        val ready = uiState.value.closureReadiness
        if (visit.pendingClose == null && (ready?.canClose != true || ready.orderId != visit.remoteOrderId)) return
        runOperation { current ->
            repository.closeAttention(visit.id, ready)
            repository.read { current.copy(closureReadiness = null).withVisits(it, visit.id) }
        }
    }

    private fun editVisit(
        change: (TableService, TableVisit) -> TableService,
        errorSource: OrderErrorSource = OrderErrorSource.OPERATION,
        onSaved: (OrderUiState) -> OrderUiState = { it }
    ) {
        val visit = uiState.value.visit?.takeUnless { it.closed } ?: return
        runOperation(blocking = false, errorSource = errorSource) { current ->
            val updated = repository.update { service ->
                val activeVisit = checkNotNull(service.openVisit(visit.table))
                check(activeVisit.id == visit.id)
                change(service, activeVisit)
            }
            onSaved(current.withVisits(updated, visit.id))
        }
    }

    private fun runOperation(blocking: Boolean = true, errorSource: OrderErrorSource = OrderErrorSource.OPERATION,
        operation: suspend (OrderUiState) -> OrderUiState) {
        if (uiState.value.busy) return
        viewModelScope.launch {
          operationMutex.withLock {
            val current = uiState.value
            mutableUiState.value = current.copy(busy = blocking, storageError = false, productSelectionChanged = false, operationMessage = null)
            try {
                val updated = operation(current)
                mutableUiState.update { latest -> updated.copy(
                    busy = false, storageError = false, operationMessage = null,
                    locations = latest.locations, selectedLocationId = latest.selectedLocationId,
                    apiTables = latest.apiTables, tablesLoading = latest.tablesLoading, tablesUserRefreshing = latest.tablesUserRefreshing,
                    menuLocations = latest.menuLocations, menuLocationId = latest.menuLocationId,
                    menuLoading = latest.menuLoading, sessionExpired = latest.sessionExpired,
                    openingSelectedTable = latest.openingSelectedTable && updated.visit?.remoteTableId == null
                ) }
                val boundLocation = updated.visit?.locationId
                if (updated.visit?.closed == false && (catalog.value == null || boundLocation != null && boundLocation != catalogLocationId)) loadCatalog()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val apiError = error as? com.example.wfappops.core.model.ApiException
                val cached = runCatching { repository.read { service -> uiState.value.withVisits(service, visitId) } }.getOrNull()
                val message = when (apiError?.code) {
                    "pos.stale_version" -> "La comanda cambió. Revisa su contenido actualizado antes de volver a anular."
                    "pos.prices_changed" -> "Los precios cambiaron. Actualiza Carta y revisa los productos del borrador antes de enviar."
                    "pos.table_occupied" -> "La mesa ya tiene una comanda abierta. Tu borrador se conserva."
                    "pos.order_not_open" -> "La comanda ya no está abierta. Tu borrador se conserva."
                    else -> apiError?.serverMessage ?: apiError?.fieldErrors?.values?.firstOrNull()
                        ?: error.message?.takeIf { error is IllegalStateException || error is IllegalArgumentException }
                }
                mutableUiState.update { (cached ?: it).copy(busy = false, storageError = true, errorSource = errorSource, operationMessage = message, openingSelectedTable = false,
                    sessionExpired = it.sessionExpired || apiError?.status == 401,
                    closureReadiness = null) }
                if (apiError?.code == "pos.prices_changed") loadMenu(true)
            }
          }
        }
    }

    private fun OrderUiState.withVisits(service: TableService, visitId: String?) = copy(
        visit = service.visits.firstOrNull { it.id == visitId && it.table == table },
        canSend = canSendToKitchen(grantedPermissions, service.visits.firstOrNull { it.id == visitId && it.table == table }),
        openTables = service.visits.filterNot { it.closed }.mapTo(mutableSetOf()) { it.table },
        attentions = service.localTableAttentions()
    )

    private companion object {
        const val TABLE = "table"
        const val VISIT_ID = "visit-id"
        const val REMOTE_TABLE_OPENED = "remote-table-opened"
        const val MENU_LOCATION = "menu-location"
    }
}
