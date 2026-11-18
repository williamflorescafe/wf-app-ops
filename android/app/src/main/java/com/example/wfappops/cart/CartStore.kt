package com.example.wfappops.cart

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.example.wfappops.orders.model.OrderLine
import com.example.wfappops.orders.data.readLine
import com.example.wfappops.orders.data.toJson
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

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
