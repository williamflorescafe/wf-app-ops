package com.example.wfappops.carta.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal fun catalogHttpsUrl(value: String): URL? = runCatching {
    URI(value).takeIf { it.scheme == "https" && it.host != null && it.userInfo == null && it.fragment == null }
        ?.toURL()
}.getOrNull()

internal fun catalogFileKey(url: URL): String = MessageDigest.getInstance("SHA-256")
    .digest(url.toExternalForm().encodeToByteArray())
    .joinToString("") { "%02x".format(it) }

internal inline fun <T> URL.catalogRequest(accept: String, read: (HttpsURLConnection) -> T): T {
    val connection = openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", accept)
        return read(connection)
    } finally {
        connection.disconnect()
    }
}

internal fun HttpsURLConnection.readCatalogBytes(maxBytes: Int): ByteArray = inputStream.use { input ->
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        if (count > maxBytes - output.size()) throw IOException("Catalog response exceeded the size limit")
        output.write(buffer, 0, count)
    }
    output.toByteArray()
}
