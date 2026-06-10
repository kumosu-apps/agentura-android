package su.kumo.byoc

import org.json.JSONObject

/**
 * Everything the SDK persists about one realm: the dynamic client this
 * installation registered there and the current AuthState (tokens).
 */
internal data class ServerRecord(
    val clientId: String,
    val registrationAccessToken: String? = null,
    val registrationClientUri: String? = null,
    val authStateJson: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("clientId", clientId)
        putOpt("registrationAccessToken", registrationAccessToken)
        putOpt("registrationClientUri", registrationClientUri)
        putOpt("authState", authStateJson)
    }.toString()

    companion object {
        fun fromJson(raw: String): ServerRecord? = try {
            val obj = JSONObject(raw)
            ServerRecord(
                clientId = obj.getString("clientId"),
                registrationAccessToken = obj.optString("registrationAccessToken").ifEmpty { null },
                registrationClientUri = obj.optString("registrationClientUri").ifEmpty { null },
                authStateJson = obj.optString("authState").ifEmpty { null },
            )
        } catch (e: Exception) {
            null
        }
    }
}
