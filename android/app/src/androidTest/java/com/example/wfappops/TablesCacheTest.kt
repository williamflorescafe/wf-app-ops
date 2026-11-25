package com.example.wfappops

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wfappops.auth.data.AuthApiException
import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.tables.*
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TablesCacheTest {
    @Test
    fun persistsAcrossInstancesExpiresAndRespectsRefreshAndAccessErrors() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = "cache-test-${UUID.randomUUID()}"
        var now = 1000L
        var calls = 0
        val layout = LocationTables(TeamLocation("San Borja", "SB", "10"),
            listOf(TableArea("1", "Terraza", listOf(AvailableTable("99", "M99", "Mesa 99", 6)))))
        val cache = TablesCache(app) { now }
        fun fetch(): LocationTables { calls++; return layout }
        assertEquals(layout, cache.get(scope, "10", fetch = ::fetch))
        assertEquals(layout, TablesCache(app) { now }.get(scope, "10", fetch = ::fetch))
        assertEquals(1, calls)
        cache.get(scope, "10", refresh = true, fetch = ::fetch)
        assertEquals(2, calls)
        now += 300001
        cache.get(scope, "10", fetch = ::fetch)
        assertEquals(3, calls)
        cache.get("$scope-another-employee", "10", fetch = ::fetch)
        assertEquals(4, calls)
        now += 300001
        assertEquals(layout, cache.get(scope, "10") { throw IOException("offline") })
        assertTrue(runCatching { cache.get(scope, "10") { throw AuthApiException(403) } }.exceptionOrNull() is AuthApiException)
        assertTrue(runCatching { cache.get(scope, "10", refresh = true) { throw IOException("offline") } }.isFailure)
    }
}
