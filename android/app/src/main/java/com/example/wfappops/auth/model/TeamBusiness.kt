package com.example.wfappops.auth.model

internal data class TeamBusiness(val id: String, val name: String, val available: Boolean, val blockedReason: String)

internal data class TeamLocation(val name: String, val code: String, val id: String = "")

internal data class TeamBusinessContext(
    val businessId: String,
    val businessName: String,
    val employeeName: String,
    val employeeId: String,
    val accessVersion: String,
    val locationScope: String = "",
    val locations: List<TeamLocation> = emptyList(),
    val membershipId: String = "",
    val permissions: Set<String> = emptySet()
)
