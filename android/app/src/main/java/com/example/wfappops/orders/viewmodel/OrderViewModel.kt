package com.example.wfappops.orders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wfappops.carta.CartaCollection
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.DispatchLineKey
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.tables.TableAttention
import com.example.wfappops.tables.TableReference
import com.example.wfappops.tables.localTableAttentions
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class OrderUiState(
    val table: Int?,
    val visit: TableVisit? = null,
    val openTables: Set<Int>? = null,
    val busy: Boolean = false,
    val storageError: Boolean = false,
    val canUpdateLines: Boolean = false,
    val addedProducts: Int = 0,
    val savedNotes: Int = 0,
    val savedComments: Int = 0,
    val kitchenUnavailable: Boolean = false,
    val locations: List<com.example.wfappops.auth.model.TeamLocation> = emptyList(),
    val selectedLocationId: String? = null,
    val apiTables: Result<com.example.wfappops.tables.LocationTables>? = null,
    val tablesLoading: Boolean = false,
    val attentions: Map<TableReference, TableAttention> = emptyMap(),
    val openingSelectedTable: Boolean = false,
    val workerName: String = ""
)

internal class OrderViewModel(
    private val repository: OrderRepository,
    private val worker: TableWorker,
    private val savedState: SavedStateHandle,
    initialTable: Int?,
    initialVisitId: String?,
    initialLocationId: String? = null,
    initialRemoteTableId: String? = null
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        OrderUiState(
            table = savedState.get<Int>(TABLE) ?: initialTable,
            canUpdateLines = repository.manual,
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

    init {
        reload()
        loadCatalog()
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

    fun reload() = runOperation { current ->
        if (current.table == null && visitId == null) {
            val service = repository.update { it.createOrder(worker) }
            val visit = service.visits.last()
            visitId = visit.id
            savedState[TABLE] = visit.table
            savedState[VISIT_ID] = visit.id
            current.copy(table = visit.table).withVisits(service, visit.id)
        } else repository.read { current.withVisits(it, visitId) }
    }

    fun loadCatalog() {
        if (catalogRequest?.isActive == true) return
        if (catalog.value?.isSuccess == true) return
        if (catalog.value?.isFailure == true) catalog.value = null
        catalogRequest = viewModelScope.launch {
            repository.observeCatalog().collect { catalog.value = it }
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
            mutableUiState.update { it.copy(tablesLoading = true) }
            try {
                val locations = repository.tableLocations()
                val selected = locations.firstOrNull { it.id == (locationId ?: uiState.value.selectedLocationId) }
                    ?: locations.firstOrNull()
                mutableUiState.update {
                    it.copy(
                        locations = locations, selectedLocationId = selected?.id,
                        apiTables = current.apiTables.takeIf { refresh && selected?.id == current.selectedLocationId && it?.isSuccess == true }
                    )
                }
                val layout = selected?.let { repository.loadTables(it.id, refresh) }
                mutableUiState.update { it.copy(apiTables = layout?.let(Result.Companion::success), tablesLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update { it.copy(apiTables = Result.failure(error), tablesLoading = false) }
            }
        }
    }

    fun openApiTable(tableId: String) = runOperation { current ->
        check(!current.tablesLoading)
        val layout = checkNotNull(current.apiTables?.getOrNull())
        val selected = layout.areas.flatMap { it.tables }.first { it.id == tableId }
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

    fun addProduct(line: OrderLine) = editVisit(
        change = { service, visit -> service.add(visit.id, line, Instant.now(), worker) },
        onSaved = { it.copy(addedProducts = it.addedProducts + 1) }
    )

    fun changeQuantity(line: OrderLine, delta: Int) = editVisit(
        change = { service, visit -> service.adjustDraft(visit.id, line, delta) }
    )

    fun saveNote(line: OrderLine, note: String) = editVisit(
        change = { service, visit -> service.editDraftNote(visit.id, line, note.trim().ifEmpty { null }) },
        onSaved = { it.copy(savedNotes = it.savedNotes + 1) }
    )

    fun saveComment(comment: String) = editVisit(
        change = { service, visit -> service.saveComment(visit.id, comment) },
        onSaved = { it.copy(savedComments = it.savedComments + 1) }
    )

    fun sendToCashier() {
        if (uiState.value.visit?.canSendToCashier != true) return
        editVisit(change = { service, visit -> service.sendToCashier(visit.id, worker, Instant.now()) })
    }

    fun sendOrder() {
        val current = uiState.value
        val draft = current.visit?.draft.orEmpty()
        if (current.busy || draft.isEmpty()) return
        if (repository.manual) {
            editVisit(change = { service, visit ->
                check(visit.draft == draft)
                service.sendLocally(visit.id, worker, Instant.now())
            })
        } else mutableUiState.update { it.copy(kitchenUnavailable = true) }
    }

    fun dismissKitchenUnavailable() = mutableUiState.update { it.copy(kitchenUnavailable = false) }

    fun advanceLine(dispatchIndex: Int, lineIndex: Int, expected: KitchenItemStatus) {
        if (!repository.manual) return
        editVisit(change = { service, visit ->
            service.advanceOrderLine(DispatchLineKey(visit.id, dispatchIndex, lineIndex), expected, Instant.now())
        })
    }

    fun cancelLines(dispatchIndex: Int, lineIndices: Set<Int>) {
        if (!repository.manual || lineIndices.isEmpty()) return
        editVisit(change = { service, visit ->
            service.cancelLocalLines(visit.id, dispatchIndex, lineIndices, worker, Instant.now())
        })
    }

    private fun editVisit(
        change: (TableService, TableVisit) -> TableService,
        onSaved: (OrderUiState) -> OrderUiState = { it }
    ) {
        val visit = uiState.value.visit?.takeUnless { it.closed } ?: return
        runOperation { current ->
            val updated = repository.update { service ->
                val activeVisit = checkNotNull(service.openVisit(visit.table))
                check(activeVisit.id == visit.id)
                change(service, activeVisit)
            }
            onSaved(current.withVisits(updated, visit.id))
        }
    }

    private fun runOperation(operation: suspend (OrderUiState) -> OrderUiState) {
        val current = uiState.value
        if (current.busy) return
        mutableUiState.value = current.copy(busy = true, storageError = false)
        viewModelScope.launch {
            try {
                val updated = operation(current)
                mutableUiState.update { latest -> updated.copy(
                    busy = false, storageError = false,
                    locations = latest.locations, selectedLocationId = latest.selectedLocationId,
                    apiTables = latest.apiTables, tablesLoading = latest.tablesLoading,
                    openingSelectedTable = latest.openingSelectedTable && updated.visit?.remoteTableId == null
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.update { it.copy(busy = false, storageError = true, openingSelectedTable = false) }
            }
        }
    }

    private fun OrderUiState.withVisits(service: TableService, visitId: String?) = copy(
        visit = service.visits.firstOrNull { it.id == visitId && it.table == table },
        openTables = service.visits.filterNot { it.closed }.mapTo(mutableSetOf()) { it.table },
        attentions = service.localTableAttentions()
    )

    private companion object {
        const val TABLE = "table"
        const val VISIT_ID = "visit-id"
        const val REMOTE_TABLE_OPENED = "remote-table-opened"
    }
}
