package com.example.wfappops.tables

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val tableZone = ZoneId.of("America/Lima")
private val tableLocale = Locale.forLanguageTag("es-PE")

internal val tableActivityTime = DateTimeFormatter.ofPattern("h:mm a", tableLocale).withZone(tableZone)
internal val tableActivityDay = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", tableLocale)

internal fun Instant.tableLocalDate(): LocalDate = atZone(tableZone).toLocalDate()
