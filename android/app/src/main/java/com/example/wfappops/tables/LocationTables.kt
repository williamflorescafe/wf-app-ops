package com.example.wfappops.tables

import com.example.wfappops.auth.model.TeamLocation

/** Distribución del local; no contiene el estado de servicio de las mesas. */
internal data class LocationTables(val location: TeamLocation, val areas: List<TableArea>)

internal data class TableArea(val id: String, val name: String, val tables: List<AvailableTable>)

internal data class AvailableTable(val id: String, val code: String, val name: String, val seats: Int)

/** Busca en el ambiente elegido sin convertir códigos de mesa en números ni alterar el orden del API. */
internal fun LocationTables.filterAreas(areaId: String?, query: String): List<TableArea> {
    val text = query.trim()
    return areas.filter { it.id == areaId }.map { area ->
        area.copy(tables = area.tables.filter {
            text.isEmpty() || it.name.contains(text, ignoreCase = true) || it.code.contains(text, ignoreCase = true)
        })
    }.filter { it.tables.isNotEmpty() }
}
