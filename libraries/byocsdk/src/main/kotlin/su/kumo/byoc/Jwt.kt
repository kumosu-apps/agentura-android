package su.kumo.byoc

import android.util.Base64
import org.json.JSONObject

internal object Jwt {
    /** Decodes a JWT payload without verifying the signature (claims are
     *  informational client-side; the realm verifies tokens server-side). */
    fun payload(token: String?): JSONObject? {
        if (token.isNullOrEmpty()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            JSONObject(String(decoded, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}
