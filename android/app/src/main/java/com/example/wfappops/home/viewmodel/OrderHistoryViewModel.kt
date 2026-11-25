package com.example.wfappops.home.viewmodel

import androidx.lifecycle.ViewModel
import com.example.wfappops.home.model.productsFor
import androidx.lifecycle.viewModelScope
import com.example.wfappops.home.model.HomeOrderFilter
import com.example.wfappops.home.model.OrderDay
import com.example.wfappops.home.model.OrderHistoryWindow
import com.example.wfappops.home.model.operationalOrders
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.RegisteredOrderStatus
import com.example.wfappops.orders.model.ServiceTable
import com.example.wfappops.orders.model.ordersBy
import com.example.wfappops.tables.tableZone
import com.example.wfappops.tables.TableReference
import com.example.wfappops.tables.TableAttention
import com.example.wfappops.tables.localTableAttentions
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

internal data class OrderHistoryScope(val storageId: String, val employeeId: String)

internal data class OrderOverview(
    val orders: List<RegisteredOrder>, val tables: List<ServiceTable>,
    val attentions: Map<TableReference, TableAttention> = emptyMap()
)

internal data class OrderHistoryUiState(
    val scope: OrderHistoryScope? = null,
    val result: Result<OrderOverview>? = null,
    val day: LocalDate = LocalDate.now(tableZone)
) {
    private val displayableOrders = result?.map { it.orders.filter { order -> order.hasDisplayableProducts } }
    private val personalOrders = displayableOrders?.map { it.ordersBy(checkNotNull(scope).employeeId) }
    val myOpenOrders = personalOrders?.map { orders ->
        orders.filter { it.status !in setOf(RegisteredOrderStatus.CLOSED, RegisteredOrderStatus.CANCELED) }
            .sortedWith(compareByDescending<RegisteredOrder> { it.lastActivityAt }.thenByDescending { it.number })
    }
    private val preparingOrders = myOpenOrders?.map { orders -> orders.filter { it.productsFor(HomeOrderFilter.PREPARING).isNotEmpty() } }
    private val readyOrders = myOpenOrders?.map { orders -> orders.filter { it.productsFor(HomeOrderFilter.READY).isNotEmpty() } }
    private val recentOrders = displayableOrders?.map { orders ->
        val window = OrderHistoryWindow(day)
        orders.filter { it.startedAt in window }
            .sortedWith(compareByDescending<RegisteredOrder> { it.startedAt }.thenByDescending { it.number })
    }
    private val myRecentOrders = recentOrders?.map { it.ordersBy(checkNotNull(scope).employeeId) }

    fun orders(onlyMine: Boolean) = if (onlyMine) myRecentOrders else recentOrders

    fun homeOrders(filter: HomeOrderFilter) = when (filter) {
        HomeOrderFilter.ALL -> myOpenOrders
        HomeOrderFilter.PREPARING -> preparingOrders
        HomeOrderFilter.READY -> readyOrders
    }

    val tables = result?.map { it.tables }
    val attentions = result?.getOrNull()?.attentions.orEmpty()
}

internal class OrderHistoryViewModel(
    private val repository: (String) -> OrderRepository,
    private val clock: Clock = Clock.system(tableZone)
) : ViewModel() {
    private val state = MutableStateFlow(OrderHistoryUiState())
    val uiState = state.asStateFlow()
    private var request: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun load(scope: OrderHistoryScope, refresh: Boolean = false) {
        val current = state.value
        val today = LocalDate.ofInstant(clock.instant(), tableZone)
        if (!refresh && current.scope == scope && current.day == today) return
        request?.cancel()
        if (current.scope != scope || current.day != today) state.value = OrderHistoryUiState(scope, day = today)
        request = viewModelScope.launch {
            try {
                val source = repository(scope.storageId)
                orderDays(clock).flatMapLatest { date ->
                    // observe ejecuta esta proyección en IO; la UI recibe las listas preparadas.
                    source.observe { service ->
                        OrderHistoryUiState(
                            scope,
                            Result.success(OrderOverview(service.operationalOrders(OrderHistoryWindow(date)), service.serviceTables(), service.localTableAttentions())),
                            day = date
                        )
                    }
                }.collect { state.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = OrderHistoryUiState(scope, Result.failure(error), LocalDate.ofInstant(clock.instant(), tableZone))
            }
        }
    }

    fun clear() {
        request?.cancel()
        state.value = OrderHistoryUiState()
    }
}

/** Cambia de día aunque no haya nuevos pedidos ni se vuelva a abrir la pantalla. */
internal fun orderDays(clock: Clock) = flow {
    while (currentCoroutineContext().isActive) {
        val now = clock.instant()
        val day = OrderDay(LocalDate.ofInstant(now, tableZone))
        emit(day.date)
        delay(Duration.between(now, day.end).toMillis().coerceAtLeast(1L))
    }
}.distinctUntilChanged()
