package com.example.wfappops.kitchen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.KitchenItemStatus
import com.example.wfappops.orders.model.KitchenLine
import com.example.wfappops.orders.model.KitchenQueue
import com.example.wfappops.orders.model.kitchenQueue
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class KitchenUiState(
    val storageId: String? = null,
    val result: Result<KitchenQueue>? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val onlyMine: Boolean = false
)

internal class KitchenViewModel(
    private val repository: (String) -> OrderRepository
) : ViewModel() {
    private val state = MutableStateFlow(KitchenUiState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    private var update: Job? = null

    fun load(storageId: String, retry: Boolean = false) {
        val previous = state.value
        if (previous.storageId == storageId && !retry) return
        clear()
        state.value = KitchenUiState(storageId, onlyMine = previous.storageId == storageId && previous.onlyMine)
        observation = viewModelScope.launch {
            try {
                repository(storageId).observe { it.kitchenQueue() }.collect { queue ->
                    state.update { it.copy(result = Result.success(queue)) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(result = Result.failure(error)) }
            }
        }
    }

    fun filterByOwner(onlyMine: Boolean) = state.update { it.copy(onlyMine = onlyMine) }

    fun advance(item: KitchenLine) {
        val expected = item.line.dispatchStatus
        if (expected != KitchenItemStatus.SENT && expected != KitchenItemStatus.IN_PREPARATION) return
        val current = state.value
        val storageId = current.storageId ?: return
        if (current.saving) return
        val changedAt = Instant.now()
        state.update { it.copy(saving = true, saveFailed = false) }
        update = viewModelScope.launch {
            try {
                repository(storageId).update { it.advanceKitchenLine(item.key, expected, changedAt) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(saveFailed = true) }
            } finally {
                state.update { current ->
                    if (current.storageId == storageId) current.copy(saving = false) else current
                }
            }
        }
    }

    fun clear() {
        observation?.cancel()
        update?.cancel()
        state.value = KitchenUiState()
    }
}
