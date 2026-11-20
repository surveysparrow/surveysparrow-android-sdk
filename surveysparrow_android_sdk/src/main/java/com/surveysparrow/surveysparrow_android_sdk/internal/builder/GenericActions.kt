package com.surveysparrow.surveysparrow_android_sdk.internal.builder

import com.surveysparrow.surveysparrow_android_sdk.internal.state.FunctionStore
import org.json.JSONObject

// Schema `{"$execute": "group.fn", "args": {...}}`: runs any backend function on a tap/event.
internal object GenericActions {
    @Volatile
    var runner: ((String, JSONObject) -> Unit)? = null

    fun handler(binding: JSONObject): (Any?) -> Unit {
        val fn = binding.optString("\$execute", "")
        val args = binding.optJSONObject("args")?.toString()
        return handler@{ event ->
            // Unknown or missing function: do nothing (old/new schema mismatch must never crash).
            if (fn.isEmpty() || FunctionStore.getFunction(fn) == null) return@handler
            try {
                val params = JSONObject(args ?: "{}")
                eventToJson(event)?.let { params.put("event", it) }
                runner?.invoke(fn, params)
            } catch (_: Throwable) {
            }
        }
    }

    private fun eventToJson(event: Any?): Any? = try {
        when (event) {
            null -> null
            is Map<*, *> -> JSONObject(event)
            is String, is Number, is Boolean -> event
            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}
