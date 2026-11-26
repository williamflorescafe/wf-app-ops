package com.example.wfappops

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.notifications.*
import com.example.wfappops.orders.model.DispatchLineKey
import com.example.wfappops.orders.model.ReadyProductNotice
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class NotificationHistoryStoreTest {
    @Test fun historySurvivesNewInstancesAndIsIsolatedByBusinessAndEmployee() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val business = "history-test-${UUID.randomUUID()}"
        val recipient = ReadyNotificationRecipient(business, "worker-a", "Colaborador")
        val time = Instant.now()
        val notice = ReadyProductNotice(DispatchLineKey("visit", 0, 0), 2, "Producto de prueba", 1,
            null, "2", 12, "1")
        try {
            val store = NotificationHistoryStore(app, recipient)
            store.sync(listOf(notice), time)
            val restored = NotificationHistoryStore(app, recipient)
            assertEquals(store.read(), restored.read())
            assertTrue(restored.sync(listOf(notice), time.plusSeconds(1)).arrivals.isEmpty())
            assertTrue(NotificationHistoryStore(app, recipient.copy(employeeId = "worker-b")).read().entries.isEmpty())
            assertTrue(NotificationHistoryStore(app, recipient.copy(storageId = "$business-other")).read().entries.isEmpty())
            restored.sync(listOf(notice), time.plusSeconds(300))
            val served = NotificationHistoryStore(app, recipient).sync(emptyList(), time.plusSeconds(600))
            assertEquals(2, served.history.entries.size)
            assertTrue(served.reminders.isEmpty())
        } finally {
            app.noBackupFilesDir.listFiles().orEmpty().filter { it.name.contains(business) }.forEach { it.delete() }
        }
    }

    @Test fun concurrentObserversDoNotDuplicateReadyArrivalsOrReminders() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val business = "history-test-${UUID.randomUUID()}"
        val recipient = ReadyNotificationRecipient(business, "worker", "Colaborador")
        val notice = ReadyProductNotice(DispatchLineKey("visit", 0, 0), 1, "Producto de prueba", 1, null)
        val time = Instant.now()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val stores = List(2) { NotificationHistoryStore(app, recipient) }
            val arrivals = stores.map { store -> pool.submit<Int> { store.sync(listOf(notice), time).arrivals.size } }
            assertEquals(1, arrivals.sumOf { it.get() })
            val reminders = stores.map { store -> pool.submit<Int> { store.sync(listOf(notice), time.plusSeconds(300)).reminders.size } }
            assertEquals(1, reminders.sumOf { it.get() })
            assertEquals(2, stores.first().read().entries.size)
        } finally {
            pool.shutdownNow()
            app.noBackupFilesDir.listFiles().orEmpty().filter { it.name.contains(business) }.forEach { it.delete() }
        }
    }
}
