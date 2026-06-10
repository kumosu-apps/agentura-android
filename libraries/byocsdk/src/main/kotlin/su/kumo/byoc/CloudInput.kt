package su.kumo.byoc

import android.net.Uri

/**
 * Where the user's cloud lives. The realm domain is the single entrypoint:
 * its `/.well-known/openid-configuration` locates the OIDC server, and the
 * `byoc_console_api_url` token claim later locates the deployment API.
 */
sealed class CloudInput {
    abstract val issuer: Uri

    /** The hosted kumo.su realm — the "[Sign in with KUMO.SU]" button. */
    object KumoSu : CloudInput() {
        override val issuer: Uri = Uri.parse("https://kumo.su")
    }

    /** A self-hosted realm, e.g. "rghomelab.nl" or "nasik.kumo.su". */
    data class Domain(val domain: String) : CloudInput() {
        override val issuer: Uri = Uri.parse(
            "https://" + domain.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
        )
    }
}
