package com.example.wfappops.carta.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection.HTTP_OK
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object CatalogPhotoStore {
    private val downloads = Array(8) { Mutex() }
    private val diskLock = Any()
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_CACHE_BYTES = 16L * 1024 * 1024
    private const val MAX_BITMAP_DIMENSION = 512

    suspend fun load(context: Context, url: URL): Bitmap? = withContext(Dispatchers.IO) {
        val key = catalogFileKey(url)
        val directory = File(context.cacheDir, "catalog-images")
        val imageFile = File(directory, key)
        val download = downloads[(key.hashCode() and Int.MAX_VALUE) % downloads.size]
        download.withLock {
            val cached = synchronized(diskLock) {
                if (imageFile.length() in 1L..MAX_IMAGE_BYTES.toLong()) {
                    runCatching { imageFile.readBytes() }.getOrNull()
                } else null
            }
            val bitmap = cached?.let(::decode)
            if (bitmap != null) {
                imageFile.setLastModified(System.currentTimeMillis())
                return@withLock bitmap
            }

            val bytes = try {
                url.catalogRequest("image/*") { response ->
                    if (response.responseCode == HTTP_OK) response.readCatalogBytes(MAX_IMAGE_BYTES) else null
                }
            } catch (_: IOException) {
                null
            } ?: return@withLock null

            decode(bytes)?.also { synchronized(diskLock) { save(imageFile, bytes) } }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / sampleSize > MAX_BITMAP_DIMENSION) sampleSize *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun save(target: File, bytes: ByteArray) {
        val file = AtomicFile(target)
        try {
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: IOException) {
                file.failWrite(output)
                throw error
            }
        } catch (_: IOException) {
            return
        }
        val entries = target.parentFile?.listFiles { entry ->
            entry.isFile && entry.name.length == 64
        }.orEmpty()
        var size = entries.sumOf { it.length() }
        for (entry in entries.sortedBy { it.lastModified() }) {
            if (size <= MAX_CACHE_BYTES) break
            if (entry == target) continue
            val length = entry.length()
            if (entry.delete()) size -= length
        }
    }
}
