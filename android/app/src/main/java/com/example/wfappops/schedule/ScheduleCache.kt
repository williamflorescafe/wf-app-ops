package com.example.wfappops.schedule

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.time.DateTimeException
import java.time.Instant
import java.time.YearMonth

internal data class ScheduleScope(val businessId: String, val employeeId: String)

/** Un registro por mes confirmado, incluso cuando la respuesta contiene cero turnos. */
internal class ScheduleCache(context: Context) : SQLiteOpenHelper(
    context, File(context.noBackupFilesDir, "ops-horarios.db").absolutePath, null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE schedule_month (
                business_id TEXT NOT NULL,
                employee_id TEXT NOT NULL,
                month TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                shifts TEXT NOT NULL,
                PRIMARY KEY (business_id, employee_id, month)
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun lease(): Long = synchronized(lock) { generation }

    fun read(scope: ScheduleScope, month: YearMonth, lease: Long): ScheduleSnapshot? = synchronized(lock) {
        if (unusable || generation != lease || month !in scheduleRetention(YearMonth.now(limaZone))) return null
        try {
            readableDatabase.query(
                TABLE, arrayOf("updated_at", "shifts"), "business_id = ? AND employee_id = ? AND month = ?",
                arrayOf(scope.businessId, scope.employeeId, month.toString()), null, null, null
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                ScheduleSnapshot(JSONArray(cursor.getString(1)).readShifts(), Instant.ofEpochMilli(cursor.getLong(0)))
            }
        } catch (_: SQLiteException) {
            null
        } catch (_: JSONException) {
            null
        } catch (_: DateTimeException) {
            null
        }
    }

    fun save(scope: ScheduleScope, month: YearMonth, snapshot: ScheduleSnapshot, lease: Long): Boolean = synchronized(lock) {
        if (unusable || generation != lease) return false
        val retained = scheduleRetention(YearMonth.now(limaZone))
        if (month !in retained) return false
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues().apply {
                    put("business_id", scope.businessId)
                    put("employee_id", scope.employeeId)
                    put("month", month.toString())
                    put("updated_at", snapshot.updatedAt.toEpochMilli())
                    put("shifts", JSONArray(snapshot.shifts.map { it.toCacheJson() }).toString())
                }
                db.replaceOrThrow(TABLE, null, values)
                db.delete(TABLE, "month < ? OR month > ?", arrayOf(retained.start.toString(), retained.endInclusive.toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            true
        } catch (_: SQLiteException) {
            false
        }
    }

    /** Invalida también las consultas que aún están esperando la respuesta del servidor. */
    fun invalidate() = synchronized(lock) {
        generation++
        try {
            writableDatabase.delete(TABLE, null, null)
            unusable = false
        } catch (_: SQLiteException) {
            unusable = true
        }
    }

    private companion object {
        const val TABLE = "schedule_month"
        val lock = Any()
        var generation = 0L
        var unusable = false
    }
}
