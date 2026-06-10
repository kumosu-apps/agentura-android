package su.kumo.byoc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.os.SystemClock

/** One installed Application as reported by the console. */
data class Backend(
    val name: String,
    val appName: String,
    val installationId: String?,
    val clientId: String?,
    val phase: String?,
    /** Resolved runtime values from status.outputs, e.g. ingress URLs. */
    val outputs: Map<String, String>,
) {
    val ready: Boolean get() = phase == "Ready"

    companion object {
        internal fun fromJson(obj: JSONObject): Backend {
            val spec = obj.optJSONObject("spec") ?: JSONObject()
            val status = obj.optJSONObject("status") ?: JSONObject()
            val outputs = mutableMapOf<String, String>()
            status.optJSONObject("outputs")?.let { raw ->
                for (key in raw.keys()) outputs[key] = raw.optString(key)
            }
            return Backend(
                name = obj.optJSONObject("metadata")?.optString("name").orEmpty(),
                appName = spec.optString("appName"),
                installationId = spec.optString("installationId").ifEmpty { null },
                clientId = spec.optString("clientId").ifEmpty { null },
                phase = status.optString("phase").ifEmpty { null },
                outputs = outputs,
            )
        }
    }
}

/**
 * Client for the BYOC console API (the deployment plane discovered via the
 * byoc_console_api_url claim). All calls carry a fresh access token.
 */
class ConsoleClient internal constructor(
    private val session: KumoSession,
    private val http: OkHttpClient,
    baseUrl: String,
) {
    private val base = baseUrl.trimEnd('/')

    suspend fun me(): JSONObject = JSONObject(request("GET", "/api/me"))

    suspend fun catalog(): JSONArray = JSONArray(request("GET", "/catalog"))

    suspend fun installations(): List<Backend> {
        val raw = JSONArray(request("GET", "/installations"))
        return (0 until raw.length()).map { Backend.fromJson(raw.getJSONObject(it)) }
    }

    /** Folders the user may attach to app drives (paired-device folders plus
     *  console-created ones). */
    suspend fun syncthingFolders(): List<DriveFolder> {
        val raw = JSONArray(request("GET", "/api/syncthing/folders"))
        return (0 until raw.length()).map { DriveFolder.fromJson(raw.getJSONObject(it)) }
    }

    /** Creates an empty server-side folder owned by the user, usable as an app
     *  drive before any device is paired (sync into it later). */
    suspend fun createSyncthingFolder(label: String): DriveFolder {
        val body = JSONObject().put("label", label)
        return DriveFolder.fromJson(JSONObject(request("POST", "/api/syncthing/folders", body.toString())))
    }

    /**
     * Installs an app. The installation is attributed to this SDK client's
     * dynamic client_id so the realm owner can later see which app created it.
     */
    suspend fun install(
        appName: String,
        manifest: String? = null,
        driveSelections: Map<String, List<String>>? = null,
    ): Backend {
        val body = JSONObject().apply {
            put("appName", appName)
            put("clientId", session.clientId ?: "")
            manifest?.let { put("manifest", it) }
            driveSelections?.let { selections ->
                put("driveSelections", JSONObject().apply {
                    selections.forEach { (key, folders) ->
                        put(key, JSONArray(folders))
                    }
                })
            }
        }
        return Backend.fromJson(JSONObject(request("POST", "/installations", body.toString())))
    }

    suspend fun deleteInstallation(name: String) {
        request("DELETE", "/installations/$name")
    }

    /**
     * The "spawn my backend" primitive: returns a Ready installation of
     * [appName], installing it from the catalog when absent and polling until
     * it is reachable. Throws [KumoException.BackendNotReady] on timeout or a
     * Failed phase.
     */
    suspend fun ensureBackend(
        appName: String,
        driveSelections: Map<String, List<String>>? = null,
        timeoutMillis: Long = 300_000,
        pollMillis: Long = 5_000,
    ): Backend {
        var backend = installations().firstOrNull { it.appName == appName }
            ?: install(appName, driveSelections = driveSelections)
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (!backend.ready) {
            if (backend.phase == "Failed") {
                throw KumoException.BackendNotReady(appName, backend.phase)
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw KumoException.BackendNotReady(appName, backend.phase)
            }
            delay(pollMillis)
            backend = installations().firstOrNull { it.name == backend.name }
                ?: throw KumoException.BackendNotReady(appName, "deleted")
        }
        return backend
    }

    private suspend fun request(method: String, path: String, body: String? = null): String {
        val token = session.freshAccessToken()
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(base + path)
                .header("Authorization", "Bearer $token")
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                else -> builder.method(
                    method,
                    (body ?: "").toRequestBody("application/json".toMediaType()),
                )
            }
            http.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = try {
                        JSONObject(text).optString("error").ifEmpty { text }
                    } catch (e: Exception) {
                        text
                    }
                    throw KumoException.ConsoleError(resp.code, detail)
                }
                text
            }
        }
    }
}

/** A Syncthing folder usable as an app drive. */
data class DriveFolder(
    val id: String,
    val label: String,
    val path: String?,
) {
    companion object {
        internal fun fromJson(obj: JSONObject): DriveFolder = DriveFolder(
            id = obj.optString("id"),
            label = obj.optString("label").ifEmpty { obj.optString("id") },
            path = obj.optString("path").ifEmpty { null },
        )
    }
}
