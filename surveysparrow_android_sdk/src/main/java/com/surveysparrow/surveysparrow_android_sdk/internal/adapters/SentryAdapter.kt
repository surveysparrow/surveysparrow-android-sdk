package com.surveysparrow.surveysparrow_android_sdk.internal.adapters

import android.util.Log
import com.surveysparrow.surveysparrow_android_sdk.internal.execute.JsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal class SentryAdapter(private val domainName: String) {

    var executeBridge: Any? = null

    fun captureP0Error(error: String, source: String, context: Map<String, Any?>) {
        captureError("P0", error, source, context)
    }

    fun captureP1Error(error: String, source: String, context: Map<String, Any?>) {
        captureError("P1", error, source, context)
    }

    private fun captureError(priority: String, error: String, source: String, context: Map<String, Any?>) {
        if (domainName.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            val normalizedEvent = JSONObject().apply {
                put("errorMessage", error)
                put("tags", JSONObject().apply {
                    put("error_priority", priority)
                    put("severity", if (priority == "P0") "CRITICAL" else "HIGH")
                    put("errorType", source)
                })
                put("contexts", JSONObject(context))
            }

            val bridge = executeBridge
            if (bridge != null) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val executeFn = bridge as? suspend (String, JSONObject?) -> String?
                    executeFn?.invoke(
                        "sentry.processSentryError",
                        JSONObject().apply {
                            put("event", normalizedEvent)
                            put("sdkType", "android")
                            put("sdkVersion", JsEngine.SDK_VERSION)
                        }
                    )
                    return@launch
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

            val payload = JSONObject().apply {
                put("errorMessage", normalizedEvent.optString("errorMessage", "Unknown"))
                put("sdkType", "android")
                put("sdkVersion", JsEngine.SDK_VERSION)
                put("tags", normalizedEvent.optJSONObject("tags") ?: JSONObject().apply {
                    put("error_priority", priority)
                    put("severity", if (priority == "P0") "CRITICAL" else "HIGH")
                    put("errorType", "GENERAL")
                })
                normalizedEvent.optJSONObject("contexts")?.let { put("contexts", it) }
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
}
