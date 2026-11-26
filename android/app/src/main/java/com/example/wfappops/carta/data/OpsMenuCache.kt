package com.example.wfappops.carta.data

import android.content.Context
import android.util.AtomicFile
import com.example.wfappops.auth.data.AuthApiException
import com.example.wfappops.carta.OpsMenu
import com.example.wfappops.carta.parseOpsMenu
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Una instantánea por identidad, negocio, revisión de permisos y local; no guarda tokens. */
internal class OpsMenuCache(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val directory = context.applicationContext.noBackupFilesDir

    suspend fun get(scope: String, locationId: String, refresh: Boolean = false,
                    stillAuthorized: () -> Boolean = { true }, fetch: () -> String): OpsMenu {
        val key = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }
        return locks.getOrPut(key) { Mutex() }.withLock {
            check(stillAuthorized())
            val file = AtomicFile(File(directory, "$PREFIX$key.json"))
            val saved = runCatching {
                val json = JSONObject(file.readFully().decodeToString())
                json.getLong("checked_at") to parseOpsMenu(json.getString("payload"), locationId)
            }.getOrNull()
            val age = saved?.let { clock() - it.first }
            if (!refresh && age != null && age in 0 until MAX_AGE) return@withLock saved.second
            try {
                val payload = fetch()
                require(payload.toByteArray().size <= MAX_BYTES)
                val result = parseOpsMenu(payload, locationId)
                check(stillAuthorized())
                val bytes = JSONObject().put("checked_at", clock()).put("payload", payload).toString().toByteArray()
                val output = file.startWrite()
                try {
                    output.write(bytes)
                    file.finishWrite(output)
                } catch (error: Exception) {
                    file.failWrite(output)
                    throw error
                }
                result
            } catch (error: Exception) {
                if (error is AuthApiException && error.status in setOf(401, 403, 404, 409)) file.delete()
                // Una caché vencida nunca habilita ventas con precio/disponibilidad no confirmados.
                throw error
            }
        }
    }

    fun clear() {
        directory.listFiles().orEmpty().filter { it.isFile && it.name.startsWith(PREFIX) }.forEach { it.delete() }
    }

    private companion object {
        const val PREFIX = "ops-menu-v1-"
        const val MAX_AGE = 60_000L
        const val MAX_BYTES = 4 * 1024 * 1024
        val locks = ConcurrentHashMap<String, Mutex>()
    }
}
