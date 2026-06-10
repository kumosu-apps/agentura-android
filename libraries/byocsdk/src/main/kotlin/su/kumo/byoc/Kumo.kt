package su.kumo.byoc

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Process-wide entry point to the BYOC cloud session, initialised from the
 * Application class. Holds the OIDC session for the user's realm (kumo.su or
 * self-hosted) and gives features access to the console API — discovering and
 * provisioning backends like matrix or hermes in the user's own cloud.
 */
object Kumo {
    private lateinit var client: KumoClient
    private var session: KumoSession? = null

    fun init(context: Context, appName: String, redirectScheme: String) {
        client = KumoClient(
            context.applicationContext,
            KumoConfig(
                softwareId = context.packageName,
                appName = appName,
                redirectUri = Uri.parse("$redirectScheme://oauth"),
            ),
        )
        session = client.restoreSession()
    }

    val isSignedIn: Boolean get() = session != null

    suspend fun prepareSignIn(input: CloudInput): Intent = client.prepareSignIn(input)

    suspend fun completeSignIn(result: Intent): KumoSession {
        val s = client.completeSignIn(result)
        session = s
        return s
    }

    private fun console(): ConsoleClient {
        val s = session ?: throw KumoException.SignInRequired("no cloud session")
        return s.console()
    }

    /**
     * Finds or installs [appName] in the user's cloud and waits until it is
     * ready. Throws [KumoException.SignInRequired] when no cloud session
     * exists (e.g. the user signed in to Matrix directly).
     */
    suspend fun ensureBackend(appName: String): Backend = console().ensureBackend(appName)

    fun signOut() {
        session?.let { client.signOut(it) }
        session = null
    }
}
