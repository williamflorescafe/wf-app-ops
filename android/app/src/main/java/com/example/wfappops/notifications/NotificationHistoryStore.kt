package com.example.wfappops.notifications

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.example.wfappops.orders.model.ReadyProductNotice
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Archivo privado por negocio/colaborador, fuera de backups. No consulta ni escribe al API. */
internal class NotificationHistoryStore(context: Context, recipient: ReadyNotificationRecipient) {
    private val file = AtomicFile(File(context.noBackupFilesDir,
        "ops-notifications-${Uri.encode("${recipient.storageId.length}:${recipient.storageId}/${recipient.employeeId}")}.json"))
    private val revision = synchronized(lock) {
        revisions.getOrPut(file.baseFile.absolutePath) { MutableStateFlow(0L) }
    }

    fun read(): NotificationHistory = synchronized(lock) { readLocked() }
    fun observe() = revision.map { runCatching { read() } }.flowOn(Dispatchers.IO)

    fun sync(notices: List<ReadyProductNotice>, now: Instant): NotificationHistoryUpdate = synchronized(lock) {
        val current = readLocked()
        val update = current.reconcile(notices, now)
        if (current != update.history) {
            val stream = file.startWrite()
            try {
                stream.write(update.history.encode().toByteArray())
                file.finishWrite(stream)
            } catch (error: IOException) {
                file.failWrite(stream)
                throw error
            }
            revision.value++
        }
        update
    }

    private fun readLocked(): NotificationHistory = try {
        NotificationHistory.decode(file.readFully().decodeToString())
    } catch (error: FileNotFoundException) {
        if (file.baseFile.exists()) throw error
        NotificationHistory()
    }

    private companion object {
        val lock = Any()
        val revisions = mutableMapOf<String, MutableStateFlow<Long>>()
    }
}
