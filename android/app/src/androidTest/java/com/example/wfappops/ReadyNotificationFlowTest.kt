package com.example.wfappops

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.ActiveOpsAccess
import com.example.wfappops.auth.OpsAccess
import com.example.wfappops.notifications.*
import com.example.wfappops.orders.data.OrderRepository
import com.example.wfappops.orders.model.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ReadyNotificationFlowTest {
    @Test
    fun manualReadyTransitionReachesObserverPostsOnceAndClearsAfterServing() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = app.getSystemService(NotificationManager::class.java)!!
        val notifier = ReadyProductNotifier(app)
        assumeTrue("Android debe permitir notificaciones para comprobar la publicación", notifier.enabled())
        val business = "notice-test-${UUID.randomUUID()}"
        val recipient = ReadyNotificationRecipient(business, "test-worker", "Prueba")
        val previousAccess = ActiveOpsAccess.current
        ActiveOpsAccess.current = OpsAccess(business)
        try {
            val worker = TableWorker(recipient.employeeId, recipient.name)
            val repository = OrderRepository(app, business, localDraftEmployeeId = worker.id)
            val visit = repository.update { it.open(1, worker) }.visits.single()
            repository.update { it.add(visit.id, OrderLine("Producto de prueba", 1, 100))
                .sendLocally(visit.id, worker, Instant.now()) }
            val key = DispatchLineKey(visit.id, 0, 0)
            repository.update { it.advanceOrderLine(key, KitchenItemStatus.SENT, Instant.now()) }
            assertTrue(readyProductNotices(app, recipient).first().isEmpty())
            val observed = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000) { readyProductNotices(app, recipient).first { it.isNotEmpty() } }
            }
            repository.update { it.advanceOrderLine(key, KitchenItemStatus.IN_PREPARATION, Instant.now()) }
            val notices = observed.await()
            notifier.sync(recipient, notices)
            withTimeout(5000) {
                while (manager.activeNotifications.none { it.tag == recipient.tag }) delay(50)
            }
            val posted = manager.activeNotifications.single { it.tag == recipient.tag }
            notifier.sync(recipient, notices)
            assertEquals(posted.postTime, manager.activeNotifications.single { it.tag == recipient.tag }.postTime)
            repository.update { it.advanceOrderLine(key, KitchenItemStatus.READY, Instant.now()) }
            val served = readyProductNotices(app, recipient).first()
            assertTrue(served.isEmpty())
            notifier.sync(recipient, served)
            withTimeout(5000) {
                while (manager.activeNotifications.any { it.tag == recipient.tag }) delay(50)
            }
        } finally {
            ActiveOpsAccess.current = previousAccess
            manager.activeNotifications.filter { it.tag == recipient.tag }.forEach { manager.cancel(it.tag, it.id) }
            app.getSharedPreferences("ready-product-notifications", 0).edit().remove(recipient.tag).commit()
            app.noBackupFilesDir.listFiles().orEmpty().filter { it.name.contains(business) }.forEach { it.delete() }
        }
    }
}
