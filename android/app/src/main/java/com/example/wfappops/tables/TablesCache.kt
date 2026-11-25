package com.example.wfappops.tables

import android.content.Context
import android.util.AtomicFile
import com.example.wfappops.auth.data.AuthApi
import com.example.wfappops.auth.data.AuthApiException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.net.ssl.SSLException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Caché persistente del plano de mesas, aislada por negocio, trabajador, permisos y sede. */
internal class TablesCache(context: Context, private val clockMillis: () -> Long = System::currentTimeMillis) {
    private val app = context.applicationContext

    /** Lectura local para completar etiquetas antiguas, sin provocar consultas de red. */
    fun peek(scope: String, locationId: String): LocationTables? = runCatching {
        val key = MessageDigest.getInstance("SHA-256").digest("$scope|${locationId.length}:$locationId".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = AtomicFile(File(app.noBackupFilesDir, "ops-tables-$key.json"))
        AuthApi(app).parseTables(file.readFully().decodeToString(), locationId)
    }.getOrNull()

    suspend fun get(
        scope: String,
        locationId: String,
        refresh: Boolean = false,
        fetch: () -> LocationTables
    ): LocationTables = lock.withLock {
        val key = MessageDigest.getInstance("SHA-256").digest("$scope|${locationId.length}:$locationId".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = AtomicFile(File(app.noBackupFilesDir, "ops-tables-$key.json"))
        val saved = runCatching {
            val json = JSONObject(file.readFully().decodeToString())
            json.getLong("fetchedAt") to AuthApi(app).parseTables(json.toString(), locationId)
        }.getOrNull()
        val age = saved?.let { clockMillis() - it.first }
        if (!refresh && age != null && age in 0 until MAX_AGE) return@withLock saved.second
        val result = try {
            fetch()
        } catch (error: IOException) {
            // Nunca se ocultan denegaciones de acceso ni errores de certificados con la caché.
            if (!refresh && saved != null && error !is AuthApiException && error !is SSLException) {
                return@withLock saved.second
            }
            throw error
        }
        val payload = result.toCacheJson().put("fetchedAt", clockMillis())
        val output = file.startWrite()
        try {
            output.write(payload.toString().encodeToByteArray())
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        result
    }

    private companion object {
        val lock = Mutex()
        const val MAX_AGE = 5 * 60 * 1000L
    }
}

private fun LocationTables.toCacheJson(): JSONObject = JSONObject().put("data", JSONObject()
    .put("location", JSONObject().put("id", location.id).put("name", location.name).put("code", location.code))
    .put("areas", JSONArray(areas.map { area ->
        JSONObject().put("id", area.id).put("name", area.name).put("tables", JSONArray(area.tables.map { table ->
            JSONObject().put("id", table.id).put("name", table.name).put("code", table.code).put("seats", table.seats)
        }))
    })))
