package com.surveysparrow.surveysparrow_android_sdk.internal.state

import org.json.JSONObject

internal object FunctionStore {
    val NON_FUNCTION_KEYS = setOf("componentSchemas", "config")

    @Volatile
    var isLoaded: Boolean = false
        private set

    private val functions = mutableMapOf<String, Any>()

    fun loadFromResponse(response: JSONObject) {
        // Load every function the backend sends (non-function keys skipped).
        for (key in response.keys()) {
            if (key in NON_FUNCTION_KEYS) continue
            when (val value = response.opt(key)) {
                is String -> if (value.isNotEmpty()) functions[key] = value
                is JSONObject -> {
                    val groupMap = mutableMapOf<String, String>()
                    for (name in value.keys()) {
                        val fn = value.opt(name)
                        if (fn is String && fn.isNotEmpty()) groupMap[name] = fn
                    }
                    if (groupMap.isNotEmpty()) functions[key] = groupMap
                }
            }
        }

        isLoaded = true
    }

    @Suppress("UNCHECKED_CAST")
    fun getFunction(dotPath: String): String? {
        val parts = dotPath.split(".")
        var current: Any? = functions
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> (current as Map<String, Any>)[part]
                is String -> return null
                else -> return null
            }
        }
        return current as? String
    }

    fun reset() {
        functions.clear()
        isLoaded = false
    }
}
