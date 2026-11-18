package com.example.wfappops.orders.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wfappops.auth.ActiveOpsAccess
import com.example.wfappops.auth.hasOpsAccess
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.RegisteredOrder
import com.example.wfappops.orders.model.TableWorker
import com.example.wfappops.orders.viewmodel.OrderViewModel
import com.example.wfappops.ui.theme.WfTheme

class CreateOrderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageId = intent.getStringExtra(STORAGE_ID)
        val workerId = intent.getStringExtra(WORKER_ID)
        val workerName = intent.getStringExtra(WORKER_NAME)
        if (storageId == null || workerId == null || workerName == null || !hasOpsAccess(storageId)) {
            finish()
            return
        }

        val order by viewModels<OrderViewModel> {
            viewModelFactory {
                initializer {
                    OrderViewModel(
                        repository = OrderRepository(applicationContext, storageId),
                        worker = TableWorker(workerId, workerName),
                        simulated = ActiveOpsAccess.current?.simulated == true,
                        savedState = createSavedStateHandle(),
                        initialTable = intent.getIntExtra(TABLE, 0).takeIf { it > 0 },
                        initialVisitId = intent.getStringExtra(VISIT_ID)
                    )
                }
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            WfTheme {
                val state by order.uiState.collectAsStateWithLifecycle()
                val catalog by order.catalogState.collectAsStateWithLifecycle()
                CreateOrderScreen(
                    state = state,
                    catalog = catalog,
                    onLoadCatalog = order::loadCatalog,
                    onReload = order::reload,
                    onOpenTable = order::openTable,
                    onAddProduct = order::addProduct,
                    onChangeQuantity = order::changeQuantity,
                    onSaveNote = order::saveNote,
                    onSaveComment = order::saveComment,
                    onSendToCashier = order::sendToCashier,
                    onSendOrder = order::sendOrder,
                    onDeliverLine = order::deliverLine,
                    onDeliverDispatch = order::deliverDispatch,
                    onCancelLines = order::cancelLines,
                    onDismissKitchenUnavailable = order::dismissKitchenUnavailable,
                    onCancel = ::finish
                )
            }
        }
    }

    internal companion object {
        private const val STORAGE_ID = "order-storage"
        private const val WORKER_ID = "order-worker-id"
        private const val WORKER_NAME = "order-worker-name"
        private const val TABLE = "order-table"
        private const val VISIT_ID = "order-visit-id"

        fun intent(context: Context, storageId: String, worker: TableWorker, order: RegisteredOrder?) =
            Intent(context, CreateOrderActivity::class.java).putExtra(STORAGE_ID, storageId)
                .putExtra(WORKER_ID, worker.id).putExtra(WORKER_NAME, worker.name)
                .apply {
                    if (order != null) {
                        putExtra(TABLE, order.table)
                        putExtra(VISIT_ID, order.visitId)
                    }
                }
    }
}
