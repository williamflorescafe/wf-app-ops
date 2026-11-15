package com.example.wfappops.cart

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.AtomicFile
import com.example.wfappops.auth.ActiveOpsAccess
import com.example.wfappops.tables.OrderLine
import com.example.wfappops.tables.readLine
import com.example.wfappops.tables.toJson
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal const val DEMO_STORAGE_PREFIX = "demo-"

internal fun Context.hasCartAccess(storageId: String): Boolean {
    val access = ActiveOpsAccess.current ?: return false
    val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val activeStorageId = if (access.simulated) DEMO_STORAGE_PREFIX + access.businessId else access.businessId
    return storageId == activeStorageId && (!access.simulated || debuggable)
}

/** Carrito de la carta general. Las mesas usan el borrador de su atención en TableStore. */
internal class CartStore(context: Context, storageId: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "ops-carrito-${Uri.encode(storageId)}.json"))

    fun read(): List<OrderLine> = synchronized(lock) { readLocked() }

    fun update(change: (List<OrderLine>) -> List<OrderLine>): List<OrderLine> = synchronized(lock) {
        val current = readLocked()
        val updated = change(current)
        if (updated != current) {
            val bytes = JSONArray(updated.map { it.toJson() }).toString().encodeToByteArray()
            val stream = file.startWrite()
            try {
                stream.write(bytes)
            } catch (error: IOException) {
                file.failWrite(stream)
                throw error
            }
            file.finishWrite(stream)
            if (!file.readFully().contentEquals(bytes)) throw IOException("No se guardó el carrito")
        }
        updated
    }

    private fun readLocked(): List<OrderLine> {
        val bytes = try {
            file.readFully()
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error
            return emptyList()
        }
        val lines = JSONArray(bytes.decodeToString())
        return List(lines.length()) { readLine(lines.getJSONObject(it)) }
    }

    private companion object {
        val lock = Any()
    }
}
