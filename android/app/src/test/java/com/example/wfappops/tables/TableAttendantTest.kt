package com.example.wfappops.tables

import org.junit.Assert.assertEquals
import org.junit.Test

class TableAttendantTest {
    @Test
    fun abbreviatesSurnameAndKeepsFullNameForAccessibility() {
        val attendant = TableAttendant(" Álvaro ", " Gallegos ")
        assertEquals("Álvaro G.", attendant.shortName)
        assertEquals("Álvaro Gallegos", attendant.fullName)
    }

    @Test
    fun neverInventsInitialWhenSurnameIsMissing() {
        assertEquals("Álvaro", TableAttendant("Álvaro", " ").shortName)
    }
}
