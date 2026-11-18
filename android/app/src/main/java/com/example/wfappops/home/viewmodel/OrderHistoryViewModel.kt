package com.example.wfappops.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.RegisteredOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class OrderHistoryScope(val storageId: String, val employeeId: String)

internal data class OrderHistoryUiState(
    val scope: OrderHistoryScope? = null,
    val result: Result<List<RegisteredOrder>>? = null
) {
    val orders get() = result?.getOrNull()
}

internal class OrderHistoryViewModel(
    private val repository: (String) -> OrderRepository
) : ViewModel() {
    private val state = MutableStateFlow(OrderHistoryUiState())
    val uiState = state.asStateFlow()
    private var request: Job? = null

    fun load(scope: OrderHistoryScope, refresh: Boolean = false) {
        val current = state.value
        if (!refresh && current.scope == scope) return
        request?.cancel()
        if (current.scope != scope) state.value = OrderHistoryUiState(scope)
        request = viewModelScope.launch {
            try {
                repository(scope.storageId).observe { it.ordersBy(scope.employeeId) }.collect { orders ->
                    state.value = OrderHistoryUiState(scope, Result.success(orders))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = OrderHistoryUiState(scope, Result.failure(error))
            }
        }
    }

    fun clear() {
        request?.cancel()
        state.value = OrderHistoryUiState()
    }
}
