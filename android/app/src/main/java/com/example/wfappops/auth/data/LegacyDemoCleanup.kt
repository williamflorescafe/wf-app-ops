package com.example.wfappops.auth.data

import android.app.NotificationManager
import android.content.Context
import java.io.IOException

/** Retira únicamente archivos y avisos del antiguo usuario simulado al actualizar la app. */
internal class LegacyDemoCleanup(private val context: Context) {
    fun run() {
        val directory = context.noBackupFilesDir.canonicalFile
        directory.listFiles()?.filter { isLegacyDemoFile(it.name) }?.forEach { file ->
            if (file.canonicalFile.parentFile == directory && file.isFile && !file.delete()) {
                throw IOException("Could not remove legacy sample data")
            }
        }
        val preferences = context.getSharedPreferences("ready-product-notifications", Context.MODE_PRIVATE)
        val legacyKeys = preferences.all.keys.filter { it.startsWith("demo-") }
        if (legacyKeys.isNotEmpty()) {
            val edit = preferences.edit()
            legacyKeys.forEach(edit::remove)
            if (!edit.commit()) throw IOException("Could not remove legacy sample preferences")
        }
        context.getSystemService(NotificationManager::class.java)?.let { manager ->
            manager.activeNotifications.filter { it.tag?.startsWith("demo-") == true }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }
}

internal fun isLegacyDemoFile(name: String): Boolean =
    name.startsWith("ops-mesas-demo-") || name.startsWith("ops-carrito-demo-")
