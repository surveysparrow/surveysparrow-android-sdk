package com.surveysparrow.surveysparrow_android_sdk.internal.adapters

import android.content.Context
import android.util.Log
import com.surveysparrow.surveysparrow_android_sdk.internal.execute.JsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Error captured by the SDK without its own Throwable (keeps the capture-site stack). */
internal class SpotcheckSdkError(message: String) : Exception(message)

/**
 * Expo pattern: when the host app ships Sentry, an isolated client (see [SentryEnricher])
 * adds stack, device, OS and app context; otherwise a basic report is sent.
 * Both go only to our backend (processSentryError, then direct /sdkErrors).
 */
internal class SentryAdapter(private val domainName: String, context: Context? = null) {

    var executeBridge: Any? = null

    private val breadcrumbs = ArrayDeque<JSONObject>()

    // Loaded only when Sentry classes exist at runtime.
    private val enricher: SentryEnricher? = if (context != null && isSentryAvailable()) {
        try {
            SentryEnricher(context) { event, data -> onEnrichedEvent(event, data) }
        } catch (_: Throwable) {
            null
        }
    } else null

    // Closes the isolated Sentry hub; enricher is null when Sentry is absent.
    fun close() {
        try {
            enricher?.close()
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun addBreadcrumb(message: String, data: Map<String, Any?> = emptyMap()) {
        breadcrumbs.addLast(JSONObject().apply {
            put("category", "spotcheck")
            put("message", message)
            put("data", JSONObject(data))
            put("timestamp", System.currentTimeMillis() / 1000.0)
        })
        while (breadcrumbs.size > 30) breadcrumbs.removeFirst()
    }

    @Synchronized
    private fun breadcrumbsJson(): JSONArray = JSONArray(breadcrumbs.toList())

    fun captureP0Error(error: String, source: String, context: Map<String, Any?>, throwable: Throwable? = null) {
        captureError("P0", error, source, context, throwable)
    }

    fun captureP1Error(error: String, source: String, context: Map<String, Any?>, throwable: Throwable? = null) {
        captureError("P1", error, source, context, throwable)
    }

    private fun captureError(priority: String, error: String, source: String, context: Map<String, Any?>, throwable: Throwable?) {
        if (domainName.isEmpty()) return
        val hintData = mapOf("priority" to priority, "source" to source, "context" to context)
        val e = enricher
        if (e == null) {
            route(basicEvent(error, priority, source, context))
            return
        }
        val captured = throwable ?: SpotcheckSdkError(error)
        // Off the caller's (often main) thread: enrichment reads device info.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (e.capture(captured, hintData)) return@launch
            } catch (_: Throwable) {
                // Version mismatch or Sentry failure: fall back to the basic report.
            }
            route(basicEvent(error, priority, source, context))
        }
    }

    private fun onEnrichedEvent(sentryEvent: JSONObject, hintData: Map<String, Any?>) {
        val priority = hintData["priority"] as? String ?: "P1"
        val source = hintData["source"] as? String ?: "GENERAL"
        @Suppress("UNCHECKED_CAST")
        val context = hintData["context"] as? Map<String, Any?> ?: emptyMap()
        val values = sentryEvent.optJSONObject("exception")?.optJSONArray("values")
        val first = values?.optJSONObject(0)
        val exceptions = JSONArray()
        if (values != null) {
            for (i in 0 until values.length()) {
                val v = values.optJSONObject(i) ?: continue
                val frames = v.optJSONObject("stacktrace")?.optJSONArray("frames")
                val last10 = JSONArray()
                if (frames != null) for (j in maxOf(0, frames.length() - 10) until frames.length()) last10.put(frames.get(j))
                exceptions.put(JSONObject().apply {
                    put("type", v.optString("type"))
                    put("value", v.optString("value"))
                    put("stacktrace", last10)
                })
            }
        }
        route(basicEvent(first?.optString("value") ?: "Unknown error", priority, source, context).apply {
            put("contexts", sentryEvent.optJSONObject("contexts") ?: JSONObject())
            put("exceptions", exceptions)
            put("eventId", sentryEvent.optString("event_id"))
        })
    }

    private fun basicEvent(error: String, priority: String, source: String, context: Map<String, Any?>) = JSONObject().apply {
        put("errorMessage", error)
        put("tags", JSONObject().apply {
            put("error_priority", priority)
            put("severity", if (priority == "P0") "CRITICAL" else "HIGH")
            put("errorType", source)
        })
        put("contexts", JSONObject())
        put("extra", JSONObject(context))
        put("breadcrumbs", breadcrumbsJson())
        put("timestamp", System.currentTimeMillis() / 1000.0)
        put("platform", "android")
    }

    // processSentryError first; direct POST to /sdkErrors as fallback.
    private fun route(normalizedEvent: JSONObject) {
        val priority = normalizedEvent.optJSONObject("tags")?.optString("error_priority") ?: "P1"
        CoroutineScope(Dispatchers.IO).launch {
            val bridge = executeBridge
            if (bridge != null) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val executeFn = bridge as? suspend (String, JSONObject?) -> String?
                    val result = executeFn?.invoke(
                        "sentry.processSentryError",
                        JSONObject().apply {
                            put("event", normalizedEvent)
                            put("sdkType", "android")
                            put("sdkVersion", JsEngine.SDK_VERSION)
                        }
                    )
                    if (result == "true") return@launch
                } catch (_: Exception) { }
            }

            sendDirectToBackend(normalizedEvent, priority)
        }
    }

    private fun sendDirectToBackend(normalizedEvent: JSONObject, priority: String) {
        try {
            val url = URL("https://$domainName/api/internal/spotcheck/sdkErrors")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val contexts = JSONObject(normalizedEvent.optJSONObject("contexts")?.toString() ?: "{}")
            contexts.put("user", JSONObject().put("spotcheck_domain_name", domainName))
            val payload = JSONObject().apply {
                put("errorMessage", normalizedEvent.optString("errorMessage", "Unknown"))
                put("sdkType", "android")
                put("sdkVersion", JsEngine.SDK_VERSION)
                put("level", if (priority == "P0") "fatal" else "error")
                put("contexts", contexts)
                put("tags", normalizedEvent.optJSONObject("tags") ?: JSONObject().apply {
                    put("error_priority", priority)
                    put("severity", if (priority == "P0") "CRITICAL" else "HIGH")
                    put("errorType", "GENERAL")
                })
                normalizedEvent.optJSONObject("extra")?.let { put("extra", it) }
                normalizedEvent.optJSONArray("breadcrumbs")?.let { put("breadcrumbs", it) }
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
            }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("SpotCheckSDK", "Failed to report error to backend", e)
        }
    }

    companion object {
        // True only when the host app includes sentry-android at runtime.
        fun isSentryAvailable(): Boolean = try {
            Class.forName("io.sentry.Hub")
            Class.forName("io.sentry.android.core.DeviceInfoUtil")
            true
        } catch (_: Throwable) {
            false
        }
    }
}
