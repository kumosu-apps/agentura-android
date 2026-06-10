package su.kumo.byoc

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * RFC 7591 dynamic client registration against the realm's
 * registration_endpoint, plus the RFC 7592 liveness probe used to detect
 * clients that the realm has garbage-collected.
 */
internal class DynamicRegistrar(private val http: OkHttpClient) {

    data class Registration(
        val clientId: String,
        val registrationAccessToken: String?,
        val registrationClientUri: String?,
    )

    /** Registers a public (PKCE-only) native client. Blocking; call on IO. */
    fun register(endpoint: String, config: KumoConfig): Registration {
        val payload = JSONObject().apply {
            put("client_name", config.appName)
            put("redirect_uris", JSONArray().put(config.redirectUri.toString()))
            put("grant_types", JSONArray().put("authorization_code").put("refresh_token"))
            put("response_types", JSONArray().put("code"))
            put("token_endpoint_auth_method", "none")
            put("application_type", "native")
            put("scope", config.scopes.joinToString(" "))
            put("software_id", config.softwareId)
            config.softwareVersion?.let { put("software_version", it) }
            config.logoUri?.let { put("logo_uri", it.toString()) }
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw KumoException.RegistrationFailed("HTTP ${resp.code}: $body")
            }
            val obj = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw KumoException.RegistrationFailed("unparsable response", e)
            }
            val clientId = obj.optString("client_id")
            if (clientId.isEmpty()) {
                throw KumoException.RegistrationFailed("response missing client_id")
            }
            return Registration(
                clientId = clientId,
                registrationAccessToken = obj.optString("registration_access_token").ifEmpty { null },
                registrationClientUri = obj.optString("registration_client_uri").ifEmpty { null },
            )
        }
    }

    /**
     * Checks whether a previously registered client still exists. Realms
     * garbage-collect dynamic clients that never completed a consent, so a
     * cached registration may be stale. Network failures count as "alive" to
     * avoid churning registrations on flaky connections. Blocking; call on IO.
     */
    fun stillExists(record: ServerRecord): Boolean {
        val uri = record.registrationClientUri ?: return true
        val token = record.registrationAccessToken ?: return true
        val request = Request.Builder()
            .url(uri)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            http.newCall(request).execute().use { resp ->
                when (resp.code) {
                    401, 403, 404, 410 -> false
                    else -> true
                }
            }
        } catch (e: Exception) {
            true
        }
    }
}
