package com.example.wfappops.carta.data

import android.content.Context
import android.util.AtomicFile
import com.example.wfappops.R
import com.example.wfappops.carta.CartaCollection
import com.example.wfappops.carta.loadOrderCarta
import com.example.wfappops.carta.parseRemoteOrderCarta
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal class OrderCatalogRepository(context: Context) {
    private val app = context.applicationContext
    private val endpoint = catalogHttpsUrl(app.getString(R.string.order_catalog_url).trim())
    private val cache = endpoint?.let {
        AtomicFile(File(app.noBackupFilesDir, "order-catalog-${catalogFileKey(it)}.json"))
    }
    private var bundled: List<CartaCollection>? = null

    fun observe() = flow<Result<List<CartaCollection>>> {
        val cached = readSnapshot()
        val local = cached?.collections?.let { Result.success(it) }
            ?: bundled?.let { Result.success(it) }
            ?: loadOrderCarta(app.assets).onSuccess { bundled = it }
        if (local.isSuccess) emit(local)

        val refreshed = refreshLock.withLock { refresh(cached) }
        if (refreshed != null && refreshed != local.getOrNull()) {
            emit(Result.success(refreshed))
        } else if (local.isFailure) {
            emit(local)
        }
    }.flowOn(Dispatchers.IO)

    private fun refresh(previous: Snapshot?): List<CartaCollection>? {
        val url = endpoint ?: return null
        val file = cache ?: return null
        // Another screen can complete a refresh while this one is displaying its local copy.
        val now = System.currentTimeMillis()
        if (previous != null && now - previous.checkedAt in 0 until CHECK_INTERVAL) return previous.collections
        val saved = readSnapshot() ?: previous
        if (saved != null && now - saved.checkedAt in 0 until CHECK_INTERVAL) return saved.collections
        if (now - lastAttempt in 0 until RETRY_INTERVAL) return saved?.collections
        lastAttempt = now

        return runCatching {
            url.catalogRequest("application/json") { response ->
                saved?.etag?.let { response.setRequestProperty("If-None-Match", it) }
                when (response.responseCode) {
                    HTTP_NOT_MODIFIED -> saved?.collections?.also {
                        synchronized(snapshotLock) {
                            file.baseFile.setLastModified(System.currentTimeMillis())
                        }
                    }
                    HTTP_OK -> {
                        val payload = JSONObject(response.readCatalogBytes(MAX_CATALOG_BYTES).decodeToString())
                        val collections = parseRemoteOrderCarta(payload, app.assets)
                        saveSnapshot(file, payload, response.getHeaderField("ETag"))
                        collections
                    }
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun readSnapshot(): Snapshot? = cache?.let { file ->
        runCatching {
            val (bytes, checkedAt) = synchronized(snapshotLock) {
                file.readFully() to file.baseFile.lastModified()
            }
            val saved = JSONObject(bytes.decodeToString())
            Snapshot(
                collections = parseRemoteOrderCarta(saved.getJSONObject("catalog"), app.assets),
                etag = saved.optString("etag").takeIf { !saved.isNull("etag") && it.isNotBlank() },
                checkedAt = checkedAt
            )
        }.getOrNull()
    }

    private fun saveSnapshot(file: AtomicFile, catalog: JSONObject, etag: String?) = synchronized(snapshotLock) {
        val bytes = JSONObject().put("catalog", catalog).put("etag", etag).toString().encodeToByteArray()
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        if (!file.readFully().contentEquals(bytes)) throw IOException("Could not persist the catalog")
    }

    private data class Snapshot(val collections: List<CartaCollection>, val etag: String?, val checkedAt: Long)

    private companion object {
        val refreshLock = Mutex()
        val snapshotLock = Any()
        var lastAttempt = Long.MIN_VALUE / 2
        val CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(15)
        val RETRY_INTERVAL = TimeUnit.MINUTES.toMillis(1)
        const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
    }
}
