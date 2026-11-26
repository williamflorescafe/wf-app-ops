package com.example.wfappops.notifications

import com.example.wfappops.orders.model.ReadyProductNotice
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class LocalNotificationKind { READY, SERVE_REMINDER }

internal data class LocalNotification(
    val id: String,
    val kind: LocalNotificationKind,
    val product: String,
    val quantity: Int,
    val tableLabel: String,
    val orderNumber: Int?,
    val locationId: String?,
    val receivedAt: Instant,
    val locationName: String? = null
)

internal data class NotificationHistory(
    val entries: List<LocalNotification> = emptyList(),
    val firstSeen: Map<String, Instant> = emptyMap(),
    val lastReminded: Map<String, Instant> = emptyMap()
) {
    /** Solo el estado READY vigente puede generar recordatorios. La ausencia no borra el historial. */
    fun reconcile(notices: List<ReadyProductNotice>, now: Instant): NotificationHistoryUpdate {
        val current = notices.distinctBy { it.identity }
        val unseen = current.filter { it.identity !in firstSeen }
        val arrivals = unseen.groupBy { it.groupIdentity }.map { (_, group) ->
            val identity = group.map { it.identity }.sorted().joinToString("\n")
            group.entry("ready/${UUID.nameUUIDFromBytes(identity.toByteArray())}", LocalNotificationKind.READY, now)
        }
        val seen = firstSeen + unseen.associate { it.identity to now }
        val reminded = lastReminded.toMutableMap()
        val reminders = current.groupBy { it.groupIdentity }.mapNotNull { (groupKey, group) ->
            val due = group.any { notice ->
                val previous = reminded[notice.identity] ?: seen.getValue(notice.identity)
                Duration.between(previous, now) >= REMINDER_INTERVAL
            }
            if (!due) null else {
                group.forEach { reminded[it.identity] = now }
                // Un único recordatorio por producto/ronda; actualizarlo evita llenar el historial cada cinco minutos.
                group.entry("reminder/$groupKey", LocalNotificationKind.SERVE_REMINDER, now)
            }
        }
        val changedIds = reminders.mapTo(mutableSetOf()) { it.id }
        val updated = copy(entries = entries.filterNot { it.id in changedIds } + arrivals + reminders,
            firstSeen = seen, lastReminded = reminded)
        return NotificationHistoryUpdate(updated, arrivals, reminders)
    }

    fun encode(): String = JSONObject().put("version", 1).put("entries", JSONArray(entries.map { entry ->
        JSONObject().put("id", entry.id).put("kind", entry.kind.name).put("product", entry.product)
            .put("quantity", entry.quantity).put("table", entry.tableLabel)
            .put("order", entry.orderNumber ?: JSONObject.NULL).put("location", entry.locationId ?: JSONObject.NULL)
            .put("locationName", entry.locationName ?: JSONObject.NULL)
            .put("receivedAt", entry.receivedAt.toEpochMilli())
    })).put("firstSeen", times(firstSeen)).put("lastReminded", times(lastReminded)).toString()

    companion object {
        val REMINDER_INTERVAL: Duration = Duration.ofMinutes(5)
        fun decode(value: String): NotificationHistory {
            val root = JSONObject(value)
            require(root.getInt("version") == 1)
            val json = root.getJSONArray("entries")
            return NotificationHistory(List(json.length()) { index ->
                val entry = json.getJSONObject(index)
                LocalNotification(entry.getString("id"), LocalNotificationKind.valueOf(entry.getString("kind")),
                    entry.getString("product"), entry.getInt("quantity"), entry.getString("table"),
                    if (entry.isNull("order")) null else entry.getInt("order"),
                    if (entry.isNull("location")) null else entry.getString("location"),
                    Instant.ofEpochMilli(entry.getLong("receivedAt")),
                    if (entry.isNull("locationName")) null else entry.getString("locationName"))
            }, readTimes(root.getJSONObject("firstSeen")), readTimes(root.getJSONObject("lastReminded")))
        }
        private fun times(values: Map<String, Instant>) = JSONObject().also { json ->
            values.forEach { (key, time) -> json.put(key, time.toEpochMilli()) }
        }
        private fun readTimes(json: JSONObject) = json.keys().asSequence().associateWith {
            Instant.ofEpochMilli(json.getLong(it))
        }
    }
}

internal data class NotificationHistoryUpdate(val history: NotificationHistory,
    val arrivals: List<LocalNotification>, val reminders: List<LocalNotification>)

private fun List<ReadyProductNotice>.entry(id: String, kind: LocalNotificationKind, now: Instant) = first().let {
    LocalNotification(id, kind, it.product, sumOf { notice -> notice.quantity },
        it.tableLabel ?: it.table.toString(), it.orderNumber, it.locationId, now, it.locationName)
}

/** Tiempo desde la recepción local; nunca atribuye esta hora a una transición del servidor. */
internal fun notificationElapsedSeconds(receivedAt: Instant, now: Instant) =
    Duration.between(receivedAt, now).seconds.coerceAtLeast(0)
