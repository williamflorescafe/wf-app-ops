package com.example.wfappops.auth.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.auth.model.TeamLocation
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SessionStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "team-auth-session"))
    private val deviceFile = AtomicFile(File(context.noBackupFilesDir, "team-device-id"))

    fun deviceId(): String {
        readEncrypted(deviceFile)?.let { return it }
        val saved = try {
            // Las instalaciones existentes conservan el UUID al migrar al almacenamiento cifrado.
            UUID.fromString(deviceFile.readFully().decodeToString()).toString()
        } catch (_: FileNotFoundException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val id = saved ?: UUID.randomUUID().toString()
        writeEncrypted(deviceFile, id)
        return id
    }

    fun read(): StoredSession? = synchronized(sessionLock) { readSession() }

    private fun readSession(): StoredSession? {
        val value = readEncrypted(file) ?: return null
        // Las versiones previas guardaban únicamente el token.
        if (!value.startsWith('{')) return StoredSession(value)
        return try {
            val json = JSONObject(value)
            val businessId = json.optString("business_id").ifBlank { null }
            val cached = json.optJSONObject("verified_business")?.let { stored ->
                runCatching { stored.readVerifiedBusiness() }.getOrNull()
            }?.takeIf { it.context.businessId == businessId }
            StoredSession(json.getString("token"), businessId, cached)
        } catch (_: JSONException) {
            null
        }
    }

    fun save(session: StoredSession) = synchronized(sessionLock) {
        val json = JSONObject()
            .put("token", session.token)
            .put("business_id", session.businessId)
            .put("verified_business", session.verifiedBusiness?.toJson())
        writeEncrypted(file, json.toString())
    }

    fun clear() = synchronized(sessionLock) { file.delete() }

    /** La revalidación no puede restaurar una sesión cerrada o reemplazada durante la petición. */
    fun saveIfCurrent(expected: StoredSession, updated: StoredSession): Boolean = synchronized(sessionLock) {
        if (!sameOpsAccess(expected, readSession())) return@synchronized false
        save(updated)
        true
    }

    private fun readEncrypted(target: AtomicFile): String? = try {
        val bytes = target.readFully()
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
        if (key == null || bytes.size <= IV_SIZE) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = GCMParameterSpec(128, bytes, 0, IV_SIZE)
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString()
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: ProviderException) {
        null
    } catch (_: IOException) {
        null
    }

    private fun writeEncrypted(target: AtomicFile, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: createKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        write(target, cipher.iv + encrypted)
    }

    private fun write(target: AtomicFile, bytes: ByteArray) {
        val stream = target.startWrite()
        try {
            stream.write(bytes)
            target.finishWrite(stream)
        } catch (exception: IOException) {
            target.failWrite(stream)
            throw exception
        }
        if (!target.readFully().contentEquals(bytes)) throw IOException("Could not persist encrypted session data")
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        val sessionLock = Any()
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ops_team_auth_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

internal data class StoredSession(
    val token: String,
    val businessId: String? = null,
    val verifiedBusiness: VerifiedBusiness? = null
)

internal fun sameOpsAccess(expected: StoredSession, current: StoredSession?): Boolean =
    current != null && current.token == expected.token && current.businessId == expected.businessId &&
        current.verifiedBusiness?.context == expected.verifiedBusiness?.context

private fun VerifiedBusiness.toJson() = JSONObject()
    .put("context_audience", "ops")
    .put("verified_at", verifiedAt.toEpochMilli())
    .put("business_id", context.businessId)
    .put("business_name", context.businessName)
    .put("employee_id", context.employeeId)
    .put("employee_name", context.employeeName)
    .put("access_version", context.accessVersion)
    .put("membership_id", context.membershipId)
    .put("permissions", JSONArray(context.permissions.toList()))
    .put("location_scope", context.locationScope)
    .put("locations", JSONArray(context.locations.map {
        JSONObject().put("id", it.id).put("name", it.name).put("code", it.code)
    }))

private fun JSONObject.readVerifiedBusiness(): VerifiedBusiness {
    // Un contexto guardado con el contrato de Team debe validarse de nuevo contra OPS.
    require(getString("context_audience") == "ops")
    val locations = getJSONArray("locations")
    val permissions = getJSONArray("permissions")
    return VerifiedBusiness(
        context = TeamBusinessContext(
            businessId = getString("business_id"),
            businessName = getString("business_name"),
            employeeName = getString("employee_name"),
            employeeId = getString("employee_id"),
            accessVersion = getString("access_version"),
            membershipId = getString("membership_id"),
            permissions = List(permissions.length()) { permissions.getString(it) }.toSet(),
            locationScope = getString("location_scope"),
            locations = List(locations.length()) { index ->
                val item = locations.getJSONObject(index)
                TeamLocation(item.getString("name"), item.getString("code"), item.getString("id"))
            }
        ),
        verifiedAt = Instant.ofEpochMilli(getLong("verified_at"))
    )
}
