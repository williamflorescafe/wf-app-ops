package com.example.wfappops.carta

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.wfappops.cart.hasCartAccess
import com.example.wfappops.tables.RegisteredOrder
import com.example.wfappops.tables.TableWorker
import com.example.wfappops.ui.theme.WfTheme

class CreateOrderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageId = intent.getStringExtra(STORAGE_ID)
        val workerId = intent.getStringExtra(WORKER_ID)
        val workerName = intent.getStringExtra(WORKER_NAME)
        if (storageId == null || workerId == null || workerName == null || !hasCartAccess(storageId)) {
            finish()
            return
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            WfTheme {
                CreateOrderScreen(
                    storageId, TableWorker(workerId, workerName),
                    initialTable = intent.getIntExtra(TABLE, 0).takeIf { it > 0 },
                    initialVisitId = intent.getStringExtra(VISIT_ID),
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
