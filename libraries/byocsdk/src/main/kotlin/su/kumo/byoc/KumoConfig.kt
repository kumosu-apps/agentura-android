package su.kumo.byoc

import android.net.Uri

/**
 * Static description of the client app, used for OIDC dynamic registration
 * (RFC 7591). [softwareId] identifies the app across installations and clouds;
 * each installation still gets its own client_id, which is what the realm
 * tracks (and what installed backends are attributed to).
 */
data class KumoConfig(
    /** Stable reverse-DNS app identifier, e.g. "su.kumo.flo". */
    val softwareId: String,
    /** Human-readable name shown on the consent screen. */
    val appName: String,
    /** Custom-scheme redirect, e.g. Uri.parse("su.kumo.mycloud://oauth"). */
    val redirectUri: Uri,
    /** Optional HTTPS logo shown on the consent screen (RFC 7591 logo_uri). */
    val logoUri: Uri? = null,
    val softwareVersion: String? = null,
    val scopes: List<String> = listOf(
        "openid", "profile", "email", "offline_access", "apps:install",
    ),
)
