package su.kumo.byoc

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import okhttp3.OkHttpClient
import kotlin.coroutines.resume

/**
 * Entry point of the BYOC Android SDK.
 *
 * Onboarding flow:
 * ```
 * val intent = kumo.prepareSignIn(CloudInput.Domain("rghomelab.nl"))
 * launcher.launch(intent)                       // Custom Tab sign-in
 * val session = kumo.completeSignIn(resultData) // tokens + console location
 * session.console().ensureBackend("navidrome")
 * ```
 * On later launches [restoreSession] returns a session backed by the stored
 * refresh token, so the user signs in once per cloud.
 */
class KumoClient(context: Context, internal val config: KumoConfig) {

    private val appContext = context.applicationContext
    private val authService = AuthorizationService(appContext)
    private val store = SecureStore(appContext)
    internal val http = OkHttpClient()
    private val registrar = DynamicRegistrar(http)

    /**
     * Resolves the realm (OIDC discovery), makes sure this installation has a
     * live dynamic client there (registering or re-registering as needed), and
     * returns the browser Intent for the PKCE authorization flow.
     */
    suspend fun prepareSignIn(input: CloudInput): Intent {
        val issuer = input.issuer
        val serviceConfig = discover(issuer)
        val record = ensureRegistered(issuer, serviceConfig)
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            record.clientId,
            ResponseTypeValues.CODE,
            config.redirectUri,
        )
            .setScopes(config.scopes)
            .build()
        store.put(KEY_PENDING_ISSUER, issuer.toString())
        return authService.getAuthorizationRequestIntent(request)
    }

    /**
     * Exchanges the authorization result for tokens. Throws
     * [KumoException.ClientGone] if the realm no longer knows our client (the
     * cached registration is dropped; restart from [prepareSignIn]).
     */
    suspend fun completeSignIn(result: Intent): KumoSession {
        val issuerRaw = store.get(KEY_PENDING_ISSUER)
            ?: throw KumoException.AuthFailed("no sign-in in progress")
        val response = AuthorizationResponse.fromIntent(result)
        val exception = AuthorizationException.fromIntent(result)
        if (response == null) {
            if (exception?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code ||
                exception?.code == AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW.code
            ) {
                throw KumoException.UserCancelled()
            }
            throw KumoException.AuthFailed(exception?.errorDescription ?: exception?.error ?: "no response", exception)
        }

        val authState = AuthState(response, exception)
        val tokenResponse = try {
            performTokenRequest(response)
        } catch (e: AuthorizationExceptionWrapper) {
            if (e.isInvalidClient()) {
                clearRegistration(issuerRaw)
                throw KumoException.ClientGone(issuerRaw)
            }
            throw KumoException.AuthFailed(e.cause.errorDescription ?: e.cause.error ?: "token exchange failed", e.cause)
        }
        authState.update(tokenResponse, null)

        val record = loadRecord(issuerRaw)
            ?: throw KumoException.AuthFailed("registration record lost for $issuerRaw")
        saveRecord(issuerRaw, record.copy(authStateJson = authState.jsonSerializeString()))
        store.put(KEY_LAST_ISSUER, issuerRaw)
        store.remove(KEY_PENDING_ISSUER)
        return KumoSession(this, issuerRaw, authState)
    }

    /** Restores the most recent signed-in session, or null if none usable. */
    fun restoreSession(): KumoSession? {
        val issuer = store.get(KEY_LAST_ISSUER) ?: return null
        val record = loadRecord(issuer) ?: return null
        val json = record.authStateJson ?: return null
        val authState = try {
            AuthState.jsonDeserialize(json)
        } catch (e: Exception) {
            return null
        }
        if (authState.refreshToken == null && !authState.isAuthorized) return null
        return KumoSession(this, issuer, authState)
    }

    /** Forgets tokens for the issuer (the dynamic client itself is left to the
     *  realm's lifecycle — its owner can revoke it from the console). */
    fun signOut(session: KumoSession) {
        loadRecord(session.issuer)?.let {
            saveRecord(session.issuer, it.copy(authStateJson = null))
        }
        if (store.get(KEY_LAST_ISSUER) == session.issuer) store.remove(KEY_LAST_ISSUER)
    }

    /**
     * Returns a valid access token, refreshing it if needed. Throws
     * [KumoException.SignInRequired] (or [KumoException.ClientGone] when the
     * realm dropped our client) once refresh is no longer possible.
     */
    internal suspend fun freshAccessToken(session: KumoSession): String =
        suspendCancellableCoroutine { cont ->
            session.authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                when {
                    accessToken != null -> {
                        loadRecord(session.issuer)?.let {
                            saveRecord(session.issuer, it.copy(authStateJson = session.authState.jsonSerializeString()))
                        }
                        cont.resume(accessToken)
                    }
                    ex != null && isInvalidClient(ex) -> {
                        clearRegistration(session.issuer)
                        cont.resumeWith(Result.failure(KumoException.ClientGone(session.issuer)))
                    }
                    else -> {
                        signOut(session)
                        cont.resumeWith(
                            Result.failure(
                                KumoException.SignInRequired(ex?.errorDescription ?: ex?.error ?: "token refresh failed")
                            )
                        )
                    }
                }
            }
        }

    fun dispose() {
        authService.dispose()
    }

    // --- internals ---

    private suspend fun discover(issuer: Uri): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(issuer) { config, ex ->
                when {
                    config != null -> cont.resume(config)
                    else -> cont.resumeWith(
                        Result.failure(KumoException.DiscoveryFailed(issuer.toString(), ex))
                    )
                }
            }
        }

    /**
     * Returns the cached dynamic client for the issuer after verifying it still
     * exists (realms GC clients that never reach consent); registers a new one
     * otherwise.
     */
    private suspend fun ensureRegistered(
        issuer: Uri,
        serviceConfig: AuthorizationServiceConfiguration,
    ): ServerRecord = withContext(Dispatchers.IO) {
        val key = issuer.toString()
        loadRecord(key)?.let { cached ->
            if (registrar.stillExists(cached)) return@withContext cached
            clearRegistration(key)
        }
        // Hydra only advertises registration_endpoint when explicitly configured
        // (WEBFINGER_OIDC_DISCOVERY_CLIENT_REGISTRATION_URL); fall back to its
        // well-known path on the *issuer* host (which may differ from the typed
        // realm domain, e.g. auth.<realm>). A realm without registration at all
        // still fails cleanly inside register().
        val issuerBase = serviceConfig.discoveryDoc?.issuer ?: issuer.toString()
        val endpoint = serviceConfig.registrationEndpoint?.toString()
            ?: (issuerBase.trimEnd('/') + "/oauth2/register")
        val registration = registrar.register(endpoint, config)
        val record = ServerRecord(
            clientId = registration.clientId,
            registrationAccessToken = registration.registrationAccessToken,
            registrationClientUri = registration.registrationClientUri,
        )
        saveRecord(key, record)
        record
    }

    private suspend fun performTokenRequest(response: AuthorizationResponse): TokenResponse =
        suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                when {
                    tokenResponse != null -> cont.resume(tokenResponse)
                    else -> cont.resumeWith(
                        Result.failure(
                            AuthorizationExceptionWrapper(
                                ex ?: AuthorizationException.GeneralErrors.NETWORK_ERROR
                            )
                        )
                    )
                }
            }
        }

    private fun isInvalidClient(ex: AuthorizationException): Boolean =
        ex.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR && ex.error == "invalid_client"

    private class AuthorizationExceptionWrapper(override val cause: AuthorizationException) :
        Exception(cause) {
        fun isInvalidClient(): Boolean =
            cause.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR && cause.error == "invalid_client"
    }

    internal fun loadRecord(issuer: String): ServerRecord? =
        store.get(recordKey(issuer))?.let { ServerRecord.fromJson(it) }

    private fun saveRecord(issuer: String, record: ServerRecord) {
        store.put(recordKey(issuer), record.toJson())
    }

    private fun clearRegistration(issuer: String) {
        store.remove(recordKey(issuer))
        if (store.get(KEY_LAST_ISSUER) == issuer) store.remove(KEY_LAST_ISSUER)
    }

    private fun recordKey(issuer: String) = "server:$issuer"

    private companion object {
        const val KEY_LAST_ISSUER = "lastIssuer"
        const val KEY_PENDING_ISSUER = "pendingIssuer"
    }
}
