package com.example.wfappops.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun JSONArray.readShifts(): List<ScheduledShift> = List(length()) { index ->
    val row = getJSONObject(index)
    ScheduledShift(
        row.getString("id"),
        LocalDate.parse(row.getString("work_date")),
        ZoneId.of(row.getString("timezone")),
        row.getString("kind"),
        row.getJSONObject("shift").getString("name"),
        row.instantOrNull("start_at"),
        row.instantOrNull("end_at"),
        row.getInt("break_minutes"),
        row.getInt("scheduled_minutes")
    )
}

private fun JSONObject.instantOrNull(key: String): Instant? = if (isNull(key)) null else Instant.parse(getString(key))

internal fun ScheduledShift.toCacheJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("work_date", date.toString())
    .put("timezone", zone.id)
    .put("kind", kind)
    .put("shift", JSONObject().put("name", name))
    .put("start_at", start?.toString() ?: JSONObject.NULL)
    .put("end_at", end?.toString() ?: JSONObject.NULL)
    .put("break_minutes", breakMinutes)
    .put("scheduled_minutes", scheduledMinutes)
