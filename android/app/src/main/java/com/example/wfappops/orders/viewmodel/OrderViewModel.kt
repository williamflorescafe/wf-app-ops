package com.example.wfappops.orders.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wfappops.carta.CartaCollection
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.DispatchLineKey
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.model.TableService
import com.example.wfappops.orders.model.TableVisit
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.orders.model.serviceTableNumbers
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class OrderUiState(
    val table: Int?,
    val visit: TableVisit? = null,
    val openTables: Set<Int>? = null,
    val busy: Boolean = false,
    val storageError: Boolean = false,
    val simulated: Boolean,
    val addedProducts: Int = 0,
    val savedNotes: Int = 0,
    val savedComments: Int = 0,
    val kitchenUnavailable: Boolean = false
)

internal class OrderViewModel(
    private val repository: OrderRepository,
    private val worker: TableWorker,
    simulated: Boolean,
    private val savedState: SavedStateHandle,
    initialTable: Int?,
    initialVisitId: String?
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        OrderUiState(table = savedState.get<Int>(TABLE) ?: initialTable, simulated = simulated)
    )
    val uiState = mutableUiState.asStateFlow()
    private val catalog = MutableStateFlow<Result<List<CartaCollection>>?>(null)
    val catalogState = catalog.asStateFlow()
    private var catalogRequest: Job? = null
    private var visitId: String? = savedState.get<String>(VISIT_ID) ?: initialVisitId

    init {
        reload()
    }

    fun reload() = runOperation { current ->
        repository.read { current.withVisits(it, visitId) }
    }

    fun loadCatalog() {
        if (catalogRequest?.isActive == true) return
        if (catalog.value?.isFailure == true) catalog.value = null
        catalogRequest = viewModelScope.launch {
            repository.observeCatalog().collect { catalog.value = it }
        }
    }

    fun openTable(table: Int) = runOperation { current ->
        require(table in serviceTableNumbers)
        val service = repository.update { it.open(table, worker) }
        val visit = checkNotNull(service.openVisit(table))
        visitId = visit.id
        savedState[TABLE] = table
        savedState[VISIT_ID] = visit.id
        current.copy(table = table).withVisits(service, visit.id)
    }

    fun addProduct(line: OrderLine) = editVisit(
        change = { service, visit -> service.add(visit.id, line) },
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
        if (current.simulated) {
            val sentAt = Instant.now()
            editVisit(change = { service, visit ->
                check(visit.draft == draft)
                service.sendLocally(visit.id, worker, sentAt, receivedAt = Instant.now())
            })
        } else {
            mutableUiState.update { it.copy(kitchenUnavailable = true) }
        }
    }

    fun dismissKitchenUnavailable() = mutableUiState.update { it.copy(kitchenUnavailable = false) }

    fun deliverLine(dispatchIndex: Int, lineIndex: Int) {
        if (!uiState.value.simulated) return
        val deliveredAt = Instant.now()
        editVisit(change = { service, visit ->
            service.deliverLine(DispatchLineKey(visit.id, dispatchIndex, lineIndex), deliveredAt)
        })
    }

    fun deliverDispatch(dispatchIndex: Int) {
        if (!uiState.value.simulated) return
        val deliveredAt = Instant.now()
        editVisit(change = { service, visit ->
            service.deliverDispatch(visit.id, dispatchIndex, deliveredAt)
        })
    }

    fun cancelLines(dispatchIndex: Int, lineIndices: Set<Int>) {
        if (!uiState.value.simulated || lineIndices.isEmpty()) return
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
                mutableUiState.value = operation(current).copy(busy = false, storageError = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.update { it.copy(busy = false, storageError = true) }
            }
        }
    }

    private fun OrderUiState.withVisits(service: TableService, visitId: String?) = copy(
        visit = service.visits.firstOrNull { it.id == visitId && it.table == table },
        openTables = service.visits.filterNot { it.closed }.mapTo(mutableSetOf()) { it.table }
    )

    private companion object {
        const val TABLE = "table"
        const val VISIT_ID = "visit-id"
    }
}
