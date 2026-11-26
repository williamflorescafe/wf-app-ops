package com.example.wfappops

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.orders.data.TableStore
import com.example.wfappops.orders.model.*
import java.io.File
import java.time.Instant
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalDraftNumberPersistenceTest {
    @Test fun restartKeepsConsumedNumbersAndLegacyArrayMigratesWithoutLosingDrafts() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "draft-numbers-test-${UUID.randomUUID()}"
        val file = File(app.noBackupFilesDir, "ops-mesas-${Uri.encode(namespace)}.json")
        val start = Instant.now()
        fun draft(id: String, offset: Long) = TableVisit(id, 38 + offset.toInt(), apiBacked = true,
            openedAt = start.plusSeconds(offset), draft = listOf(OrderLine("Producto de prueba", 1, 100)))
        try {
            val store = TableStore(app, namespace)
            store.update { TableService(listOf(draft("one", 0), draft("two", 1))) }
            // Formato anterior: solo el arreglo de visitas, sin numeración local.
            val legacy = JSONObject(file.readText()).getJSONArray("visits")
            for (i in 0 until legacy.length()) legacy.getJSONObject(i).remove("localDraftNumber")
            file.writeText(legacy.toString())
            store.update { it }
            assertEquals(listOf(1, 2), TableStore(app, namespace).read().visits.map { it.localDraftNumber })
            store.update { it.copy(visits = it.visits.map { visit -> if (visit.id == "two") visit.copy(draft = emptyList()) else visit }) }
            val restored = TableStore(app, namespace)
            assertEquals(3, restored.read().nextLocalDraftNumber)
            restored.update { it.copy(visits = it.visits + draft("three", 2)) }
            assertEquals(3, restored.read().visits.last().localDraftNumber)
            restored.update { it.copy(visits = it.visits.map { visit -> visit.copy(draft = emptyList()) }) }
            TableStore(app, namespace).update { it.copy(visits = it.visits + draft("fresh", 3)) }
            assertEquals(1, restored.read().visits.last().localDraftNumber)
        } finally { file.delete() }
    }
}
