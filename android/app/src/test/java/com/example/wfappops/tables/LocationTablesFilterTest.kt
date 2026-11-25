package com.example.wfappops.tables

import com.example.wfappops.auth.model.TeamLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTablesFilterTest {
    private val layout = LocationTables(TeamLocation("Local", "LOC", "1"), listOf(
        TableArea("20", "Salón", listOf(
            AvailableTable("900", "T03", "Mesa ventana", 4),
            AvailableTable("400", "T01", "Mesa centro", 2)
        )),
        TableArea("30", "Terraza", listOf(AvailableTable("800", "T03", "Mesa terraza", 4)))
    ))

    @Test
    fun searchUsesCodeAndSelectedAreaWithoutMixingTablesWithTheSameCode() {
        assertEquals(listOf("900"), layout.filterAreas("20", " t03 ").single().tables.map { it.id })
    }

    @Test
    fun searchUsesNameAndKeepsServerOrder() {
        assertEquals(listOf("900", "400"), layout.filterAreas("20", "mesa").single().tables.map { it.id })
        assertEquals("400", layout.filterAreas("20", "CENTRO").single().tables.single().id)
    }

    @Test
    fun unknownAreaOrQueryDoesNotFallBackToOtherAreas() {
        assertTrue(layout.filterAreas("99", "").isEmpty())
        assertTrue(layout.filterAreas("20", "terraza").isEmpty())
    }
}
