package com.example.wfappops.orders.data

import android.content.Context
import com.example.wfappops.auth.hasOpsAccess
import com.example.wfappops.carta.data.OrderCatalogRepository
import com.example.wfappops.orders.model.TableService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class OrderRepository(context: Context, private val storageId: String) {
    private val appContext = context.applicationContext
    private val store = TableStore(appContext, storageId)
    private val catalog by lazy { OrderCatalogRepository(appContext) }

    fun observeCatalog() = catalog.observe()

    fun <T> observe(select: (TableService) -> T) =
        store.changes.map { read(select) }.distinctUntilChanged()

    suspend fun <T> read(select: (TableService) -> T): T = withContext(Dispatchers.IO) {
        check(appContext.hasOpsAccess(storageId))
        select(store.read())
    }

    suspend fun update(change: (TableService) -> TableService): TableService = withContext(Dispatchers.IO) {
        store.update { current ->
            check(appContext.hasOpsAccess(storageId))
            change(current)
        }
    }
}
