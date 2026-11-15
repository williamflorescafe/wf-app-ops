package com.example.wfappops.auth

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import com.example.wfappops.schedule.SchedulePage
import com.example.wfappops.schedule.collectScheduleMonth
import com.example.wfappops.schedule.readShifts
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.DateTimeException
import java.time.YearMonth
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
            .put("password", password)
            .put("device_id", deviceId)
            .put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100))
        return parseLoginToken(request("POST", "team/auth/login", body))
    }

    fun parseLoginToken(response: String): String = decode { JSONObject(response).getJSONObject("data").getString("token") }
        .takeIf { it.isNotBlank() && it != "null" } ?: throw AuthApiException()

    fun profileEmail(token: String): String {
        val response = request("GET", "team/me", token = token)
        return decode { JSONObject(response).getJSONObject("data").getString("email") }
            .takeIf { it.isNotBlank() && it != "null" } ?: throw AuthApiException()
    }

    fun businesses(token: String): List<TeamBusiness> {
        val response = request("GET", "team/businesses", token = token)
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
        val response = request("GET", "team/context", token = token, businessId = businessId)
        return parseBusinessContext(response, businessId)
    }

    fun schedule(token: String, context: TeamBusinessContext, month: YearMonth) = collectScheduleMonth(month) { cursor ->
        val query = buildString {
            append("from=${month.atDay(1)}&to=${month.atEndOfMonth()}&per_page=31")
            if (cursor != null) append("&cursor=${Uri.encode(cursor)}")
        }
        val response = request(
            "GET", "team/attendance/schedule?$query", token = token,
            businessId = context.businessId, accessVersion = context.accessVersion
        )
        try {
            decode {
                val page = JSONObject(response)
                val meta = page.getJSONObject("meta")
                SchedulePage(page.getJSONArray("data").readShifts(), if (meta.isNull("next_cursor")) null else meta.getString("next_cursor"))
            }
        } catch (_: DateTimeException) {
            throw AuthApiException()
        }
    }

    fun parseBusinessContext(response: String, expectedBusinessId: String? = null): TeamBusinessContext {
        return decode {
            val data = JSONObject(response).getJSONObject("data")
            val business = data.getJSONObject("business")
            val employee = data.getJSONObject("employee")
            val businessId = business.getString("id")
            if (expectedBusinessId != null && businessId != expectedBusinessId) throw AuthApiException()
            val locations = data.optJSONArray("locations")
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
                    TeamLocation(name, location.optString("code").takeUnless { location.isNull("code") }.orEmpty())
                }
            )
        }
    }

    fun logout(token: String) {
        try {
            request("POST", "team/auth/logout", token = token)
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
            if (status == 204) return ""
            if (status in 200..299) {
                return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val error = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            throw responseError(status, parseJson(error))
        } finally {
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

internal data class TeamBusiness(val id: String, val name: String, val available: Boolean, val blockedReason: String)

internal data class TeamLocation(val name: String, val code: String)

internal data class TeamBusinessContext(
    val businessId: String,
    val businessName: String,
    val employeeName: String,
    val employeeId: String,
    val accessVersion: String,
    val locationScope: String = "",
    val locations: List<TeamLocation> = emptyList()
)
