package com.example.wfappops.orders.model

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LocalDraftNumberTest {
    private val start = Instant.parse("2026-11-26T20:00:00Z")
    private fun draft(id: String, seconds: Long = 0) = TableVisit(id, 38 + seconds.toInt(), apiBacked = true,
        openedAt = start.plusSeconds(seconds), draft = listOf(OrderLine("Café", 1, 100)))

    @Test fun existingDraftsReceiveNumbersInCreationOrderOnce() {
        val service = TableService(listOf(draft("second", 2), draft("first", 1))).withLocalDraftNumbers()
        assertEquals(listOf(2, 1), service.visits.map { it.localDraftNumber })
        assertEquals(3, service.nextLocalDraftNumber)
        assertEquals(service, service.withLocalDraftNumbers())
    }

    @Test fun sendingOneDoesNotRenameTheOtherAndNewDraftGetsTheNextNumber() {
        val initial = TableService(listOf(draft("one"), draft("two", 1))).withLocalDraftNumbers()
        val sent = initial.copy(visits = initial.visits.map {
            if (it.id == "one") it.copy(remoteOrderId = "official", orderNumber = 12, draft = emptyList()) else it
        }).withLocalDraftNumbers()
        assertNull(sent.visits.first().localDraftNumber)
        assertEquals(2, sent.visits.last().localDraftNumber)
        val next = sent.copy(visits = sent.visits + draft("three", 2)).withLocalDraftNumbers()
        assertEquals(3, next.visits.last().localDraftNumber)
    }

    @Test fun removingTheHighestNumberDoesNotReuseItWhileAnotherDraftRemains() {
        val initial = TableService(listOf(draft("one"), draft("two", 1))).withLocalDraftNumbers()
        val removed = initial.copy(visits = initial.visits.map { if (it.id == "two") it.copy(draft = emptyList()) else it })
            .withLocalDraftNumbers()
        val next = removed.copy(visits = removed.visits + draft("three", 2)).withLocalDraftNumbers()
        assertEquals(listOf(1, null, 3), next.visits.map { it.localDraftNumber })
    }

    @Test fun cycleResetsOnlyAfterAllDraftsAreFinished() {
        val initial = TableService(listOf(draft("one"), draft("two", 1))).withLocalDraftNumbers()
        val cleared = initial.copy(visits = initial.visits.map { it.copy(draft = emptyList()) }).withLocalDraftNumbers()
        assertEquals(1, cleared.nextLocalDraftNumber)
        val next = cleared.copy(visits = cleared.visits + draft("new", 3)).withLocalDraftNumbers()
        assertEquals(1, next.visits.last().localDraftNumber)
    }

    @Test fun emptyVisitsAndAdditionalDraftsOfOfficialOrdersDoNotConsumeNumbers() {
        val empty = draft("empty").copy(draft = emptyList())
        val official = draft("official", 1).copy(remoteOrderId = "order", orderNumber = 12)
        val service = TableService(listOf(empty, official, draft("local", 2))).withLocalDraftNumbers()
        assertEquals(listOf(null, null, 1), service.visits.map { it.localDraftNumber })
    }

    @Test fun internalTableCounterIsNotAnAssignedTable() {
        val service = TableService(listOf(draft("one"))).withLocalDraftNumbers()
        val visit = service.visits.single()
        val summary = visit.copy(orderStartedAt = start).let { service.copy(visits = listOf(it)).registeredOrders().single() }
        assertEquals(38, summary.table)
        assertFalse(summary.hasAssignedTable)
        assertEquals(1, summary.localDraftNumber)
        val assigned = service.copy(visits = listOf(visit.copy(remoteTableId = "table-api", tableName = "2", orderStartedAt = start)))
        assertTrue(assigned.registeredOrders().single().hasAssignedTable)
    }
}
