package com.example.wfappops.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
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

    fun read(): StoredSession? {
        val value = readEncrypted(file) ?: return null
        // Las versiones previas guardaban únicamente el token.
        if (!value.startsWith('{')) return StoredSession(value)
        return try {
            val json = JSONObject(value)
            StoredSession(json.getString("token"), json.optString("business_id").ifBlank { null })
        } catch (_: JSONException) {
            null
        }
    }

    fun save(session: StoredSession) {
        val json = JSONObject().put("token", session.token).put("business_id", session.businessId)
        writeEncrypted(file, json.toString())
    }

    fun clear() = file.delete()

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
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ops_team_auth_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

internal data class StoredSession(val token: String, val businessId: String? = null)
