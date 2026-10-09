package com.surveysparrow.surveysparrow_android_sdk.internal.builder

import org.json.JSONArray
import org.json.JSONObject

internal class BindingResolver(
    private val state: JSONObject,
    private val styles: Map<String, Any?>,
    private val handlers: Map<String, (Any?) -> Unit>,
    private val slots: Map<String, @androidx.compose.runtime.Composable () -> Unit>,
) {
    fun resolve(value: Any?): Any? {
        if (value == null || value == JSONObject.NULL) return null

        if (value is JSONObject) {
            if (value.has("\$ref")) return resolveRef(value.getString("\$ref"))
            if (value.has("\$path")) return resolvePath(value.getString("\$path"))
            if (value.has("\$expr")) return resolveExpr(value.getString("\$expr"))
            if (value.has("\$handler")) return resolveHandler(value.getString("\$handler"))
            if (value.has("\$execute")) return GenericActions.handler(value)
            if (value.has("\$handlers")) {
                val names = value.getJSONArray("\$handlers")
                return {
                    for (i in 0 until names.length()) {
                        handlers[names.getString(i)]?.invoke(null)
                    }
                }
            }

            val map = mutableMapOf<String, Any?>()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = resolve(value.get(key))
            }
            return map
        }

        if (value is JSONArray) {
            return (0 until value.length()).map { resolve(value.get(it)) }
        }

        return value
    }

    private fun resolveRef(path: String): Any? {
        val cleanPath = if (path.startsWith("styles.")) path.removePrefix("styles.") else path
        return navigateMap(styles, cleanPath)
    }

    private fun resolvePath(path: String): Any? {
        return navigateJson(state.optJSONObject("SpotCheckState"), path)
    }

    // $expr is not supported on native; backend style functions compute values.
    @Suppress("UNUSED_PARAMETER")
    fun resolveExpr(expr: String): Any? = null

    private fun resolveHandler(name: String): ((Any?) -> Unit)? {
        return handlers[name]
    }

    fun evaluateCondition(condition: Any?): Boolean {
        if (condition == null || condition == JSONObject.NULL) return true
        if (condition is Boolean) return condition
        if (condition is String) return condition.isNotEmpty()
        if (condition is Number) return condition.toDouble() != 0.0

        if (condition is JSONObject) {
            if (condition.has("\$and")) {
                val arr = condition.getJSONArray("\$and")
                return (0 until arr.length()).all { evaluateCondition(arr.get(it)) }
            }
            if (condition.has("\$or")) {
                val arr = condition.getJSONArray("\$or")
                return (0 until arr.length()).any { evaluateCondition(arr.get(it)) }
            }
            if (condition.has("\$not")) {
                return !evaluateCondition(condition.get("\$not"))
            }
            if (condition.has("\$eq")) {
                val arr = condition.getJSONArray("\$eq")
                val left = resolve(arr.get(0))
                val right = resolve(arr.get(1))
                return left?.toString() == right?.toString()
            }
            if (condition.has("\$ne")) {
                val arr = condition.getJSONArray("\$ne")
                val left = resolve(arr.get(0))
                val right = resolve(arr.get(1))
                return left?.toString() != right?.toString()
            }
            if (condition.has("\$gt")) {
                val arr = condition.getJSONArray("\$gt")
                val left = (resolve(arr.get(0)) as? Number)?.toDouble() ?: 0.0
                val right = (resolve(arr.get(1)) as? Number)?.toDouble() ?: 0.0
                return left > right
            }
            if (condition.has("\$lt")) {
                val arr = condition.getJSONArray("\$lt")
                val left = (resolve(arr.get(0)) as? Number)?.toDouble() ?: 0.0
                val right = (resolve(arr.get(1)) as? Number)?.toDouble() ?: 0.0
                return left < right
            }

            if (condition.has("\$path")) return isTruthy(resolvePath(condition.getString("\$path")))
            if (condition.has("\$ref")) return isTruthy(resolveRef(condition.getString("\$ref")))
            if (condition.has("\$expr")) return isTruthy(resolveExpr(condition.getString("\$expr")))
        }

        return isTruthy(condition)
    }

    companion object {
        fun isTruthy(value: Any?): Boolean {
            if (value == null || value == JSONObject.NULL) return false
            if (value is Boolean) return value
            if (value is Number) return value.toDouble() != 0.0
            if (value is String) return value.isNotEmpty() && value != "false" && value != "null" && value != "0"
            return true
        }

        fun navigateJson(obj: JSONObject?, path: String): Any? {
            if (obj == null) return null
            val parts = path.split(".")
            var current: Any? = obj
            for (part in parts) {
                current = when (current) {
                    is JSONObject -> {
                        if (current.has(part)) current.get(part) else null
                    }
                    else -> null
                }
                if (current == null || current == JSONObject.NULL) return null
            }
            return current
        }

        fun navigateMap(map: Map<String, Any?>, path: String): Any? {
            val parts = path.split(".")
            var current: Any? = map
            for (part in parts) {
                current = when (current) {
                    is Map<*, *> -> current[part]
                    else -> null
                }
                if (current == null) return null
            }
            return current
        }
    }
}
