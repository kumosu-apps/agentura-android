package su.kumo.byoc

sealed class KumoException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** The realm's OIDC discovery document could not be fetched or parsed. */
    class DiscoveryFailed(issuer: String, cause: Throwable? = null) :
        KumoException("OIDC discovery failed for $issuer", cause)

    /** Dynamic client registration was rejected by the realm. */
    class RegistrationFailed(detail: String, cause: Throwable? = null) :
        KumoException("Dynamic client registration failed: $detail", cause)

    /** The user dismissed the browser sign-in. */
    class UserCancelled : KumoException("Sign-in was cancelled")

    /**
     * The dynamically registered client no longer exists on the realm (e.g.
     * garbage-collected after inactivity). The SDK has already discarded the
     * cached registration; calling prepareSignIn again registers a fresh one.
     */
    class ClientGone(issuer: String) :
        KumoException("Registered client vanished on $issuer; sign in again")

    /** Tokens are no longer refreshable; a new interactive sign-in is needed. */
    class SignInRequired(detail: String) :
        KumoException("Sign-in required: $detail")

    /** Generic OAuth failure during authorization or token exchange. */
    class AuthFailed(detail: String, cause: Throwable? = null) :
        KumoException("Authentication failed: $detail", cause)

    /** The console API returned an error. */
    class ConsoleError(val status: Int, detail: String) :
        KumoException("Console API error ($status): $detail")

    /** A backend installation did not become ready in time. */
    class BackendNotReady(appName: String, phase: String?) :
        KumoException("Backend $appName not ready (phase: ${phase ?: "unknown"})")
}
