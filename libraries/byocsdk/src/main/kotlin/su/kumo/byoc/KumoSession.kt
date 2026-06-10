package su.kumo.byoc

import net.openid.appauth.AuthState
import org.json.JSONObject

/**
 * A signed-in connection to one realm. The console API location comes from
 * the byoc_console_api_url token claim — never guessed from the hostname.
 */
class KumoSession internal constructor(
    private val client: KumoClient,
    val issuer: String,
    internal val authState: AuthState,
) {
    /** Claims of the ID token (unverified, informational). */
    val claims: JSONObject =
        Jwt.payload(authState.idToken) ?: Jwt.payload(authState.accessToken) ?: JSONObject()

    val username: String? =
        claims.optString("preferred_username").ifEmpty { null }

    val subject: String? = claims.optString("sub").ifEmpty { null }

    /** Base URL of the BYOC console API, from the byoc_console_api_url claim. */
    val consoleApiUrl: String? =
        claims.optString("byoc_console_api_url").ifEmpty { null }

    /** The dynamic client_id this installation holds on the realm. Installed
     *  backends are attributed to it, so the user can audit/clean them up. */
    val clientId: String? = client.loadRecord(issuer)?.clientId

    suspend fun freshAccessToken(): String = client.freshAccessToken(this)

    /** Typed client for the realm's console API (catalog, installations). */
    fun console(): ConsoleClient {
        val base = consoleApiUrl
            ?: throw KumoException.ConsoleError(0, "token has no byoc_console_api_url claim")
        return ConsoleClient(this, client.http, base)
    }

    fun signOut() = client.signOut(this)
}
