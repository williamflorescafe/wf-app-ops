package com.example.wfappops.auth.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.wfappops.auth.model.TeamBusiness
import com.example.wfappops.auth.model.TeamBusinessContext
import com.example.wfappops.auth.model.TeamLocation
import com.example.wfappops.tables.AvailableTable
import com.example.wfappops.tables.LocationTables
import com.example.wfappops.tables.TableArea
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class AuthApi(context: Context) {
    val isStaging = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val baseUrl = if (isStaging) {
        "https://stg-api.williamflorescafe.pe/v1"
    } else {
        "https://api.williamflorescafe.pe/v1"
    }

    fun login(email: String, password: String, deviceId: String): String {
        val body = JSONObject()
            .put("email", email)
            .put("login_mode", "email")
            .put("business_slug", "cafe-de-prueba")
            .put("document_type", "dni")
            .put("document_country", "PE")
            .put("password", password)
            .put("device_id", deviceId)
            .put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100))
        return parseLoginToken(request("POST", "ops/auth/login", body))
    }

    fun parseLoginToken(response: String): String = decode { JSONObject(response).getJSONObject("data").getString("token") }
        .takeIf { it.isNotBlank() && it != "null" } ?: throw AuthApiException()

    fun profileEmail(token: String): String {
        val response = request("GET", "ops/me", token = token)
        return decode { JSONObject(response).getJSONObject("data").getString("email") }
            .takeIf { it.isNotBlank() && it != "null" } ?: throw AuthApiException()
    }

    fun businesses(token: String): List<TeamBusiness> {
        val response = request("GET", "ops/businesses", token = token)
        return decode {
            val data = JSONObject(response).getJSONArray("data")
            List(data.length()) { index ->
                val business = data.getJSONObject(index)
                TeamBusiness(
                    id = business.getString("id"),
                    name = business.getString("name"),
                    available = business.getString("status") == "available",
                    blockedReason = business.optString("blocked_reason")
                )
            }
        }
    }

    fun businessContext(token: String, businessId: String): TeamBusinessContext {
        val response = request("GET", "ops/context", token = token, businessId = businessId)
        return parseBusinessContext(response, businessId)
    }

    fun parseBusinessContext(response: String, expectedBusinessId: String? = null): TeamBusinessContext {
        return decode {
            val data = JSONObject(response).getJSONObject("data")
            val business = data.getJSONObject("business")
            val employee = data.getJSONObject("employee")
            val businessId = business.getString("id")
            if (expectedBusinessId != null && businessId != expectedBusinessId) throw AuthApiException()
            val locations = data.optJSONArray("locations")
            val permissions = data.optJSONArray("permissions")
            TeamBusinessContext(
                businessId = businessId,
                businessName = business.getString("name"),
                employeeName = employee.getString("display_name"),
                employeeId = employee.getString("id"),
                accessVersion = data.getString("access_version"),
                locationScope = data.optString("location_scope"),
                locations = (0 until (locations?.length() ?: 0)).mapNotNull { index ->
                    val location = locations?.optJSONObject(index) ?: return@mapNotNull null
                    val name = location.optString("name")
                    if (name.isBlank() || location.isNull("name")) return@mapNotNull null
                    TeamLocation(
                        name,
                        location.optString("code").takeUnless { location.isNull("code") }.orEmpty(),
                        location.optString("id").takeUnless { location.isNull("id") }.orEmpty()
                    )
                },
                membershipId = data.optJSONObject("membership")?.getString("id").orEmpty(),
                permissions = (0 until (permissions?.length() ?: 0)).map { index ->
                    checkNotNull(permissions).getString(index)
                }.toSet()
            )
        }
    }

    fun tables(token: String, businessId: String, locationId: String): LocationTables {
        require(Regex("[1-9][0-9]*").matches(locationId))
        val response = request("GET", "ops/locations/$locationId/tables", token = token, businessId = businessId)
        return parseTables(response, locationId)
    }

    fun parseTables(response: String, expectedLocationId: String): LocationTables = decode {
        val data = JSONObject(response).getJSONObject("data")
        val location = data.getJSONObject("location")
        if (location.getString("id") != expectedLocationId) throw AuthApiException()
        val areas = data.getJSONArray("areas")
        val parsed = LocationTables(
            TeamLocation(location.getString("name"), location.getString("code"), location.getString("id")),
            List(areas.length()) { index ->
                val area = areas.getJSONObject(index)
                val tables = area.getJSONArray("tables")
                TableArea(area.getString("id"), area.getString("name"), List(tables.length()) { tableIndex ->
                    val table = tables.getJSONObject(tableIndex)
                    AvailableTable(table.getString("id"), table.getString("code"), table.getString("name"), table.getInt("seats"))
                })
            }
        )
        // IDs únicos evitan mezclar mesas y claves repetidas en la lista de Compose.
        val tableIds = parsed.areas.flatMap { it.tables }.map { it.id }
        if (parsed.areas.map { it.id }.distinct().size != parsed.areas.size ||
            tableIds.distinct().size != tableIds.size || parsed.areas.any { area -> area.tables.any { it.seats < 0 } }
        ) throw AuthApiException()
        parsed
    }

    fun logout(token: String) {
        try {
            request("POST", "ops/auth/logout", token = token)
        } catch (exception: AuthApiException) {
            if (exception.status != 401) throw exception
        }
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null,
        businessId: String? = null,
        accessVersion: String? = null
    ): String {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val connection = URL("$baseUrl/$path").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "es")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (businessId != null) connection.setRequestProperty("X-Business-Id", businessId)
            if (accessVersion != null) connection.setRequestProperty("X-Access-Version", accessVersion)
            if (body != null) {
                val bytes = body.toString().encodeToByteArray()
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }

            val status = connection.responseCode
            if (isStaging) {
                Log.d("OpsAuthApi", "$method /${path.substringBefore('?')} -> HTTP $status")
            }
            if (status == 204) return ""
            if (status in 200..299) {
                return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val error = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            val errorBody = parseJson(error)
            if (isStaging) {
                // Solo metadatos para cruzar el fallo con backend; nunca el cuerpo ni credenciales.
                val code = errorBody?.optString("code")?.take(128)?.filter { it.isLetterOrDigit() || it in "._:-" }
                val requestId = errorBody?.optString("request_id")?.take(128)?.filter { it.isLetterOrDigit() || it in "._:-" }
                Log.w("OpsAuthApi", "$method /${path.substringBefore('?')} -> HTTP $status (code=$code, request_id=$requestId)")
            }
            throw responseError(status, errorBody)
        } finally {
            if (isStaging) {
                Log.d("OpsApiTiming", "$method /${path.substringBefore('?')} completed in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms")
            }
            connection.disconnect()
        }
    }

    private fun parseJson(body: String?): JSONObject? = try {
        body?.let(::JSONObject)
    } catch (_: JSONException) {
        null
    }

    private inline fun <T> decode(parse: () -> T): T = try {
        parse()
    } catch (_: JSONException) {
        throw AuthApiException()
    }

    private fun responseError(status: Int, body: JSONObject?): AuthApiException {
        val errors = body?.optJSONObject("errors")
        val fieldErrors = errors?.keys()?.asSequence()?.mapNotNull { field ->
            errors.optJSONArray(field)?.optString(0)?.takeIf { it.isNotBlank() }?.let { field to it }
        }?.toMap().orEmpty()
        val message = body?.optString("message")?.takeIf { it.isNotBlank() && it != "null" }
        return AuthApiException(status, message, fieldErrors, body?.optString("code"))
    }
}

internal class AuthApiException(
    val status: Int? = null,
    val serverMessage: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val code: String? = null
) : IOException()
