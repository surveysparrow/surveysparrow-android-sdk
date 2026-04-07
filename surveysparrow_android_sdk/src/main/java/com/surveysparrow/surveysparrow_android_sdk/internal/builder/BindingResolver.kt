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

    fun resolveExpr(expr: String): Any? {
        val spotCheckState = state.optJSONObject("SpotCheckState") ?: JSONObject()
        return evaluateExprKotlin(expr, spotCheckState)
    }

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

        private fun readStatePath(state: JSONObject, path: String): Any? {
            val cleanPath = path
                .replace("state?.", "")
                .replace("state.", "")
                .replace("?.", ".")
            return navigateJson(state, cleanPath)
        }

        private fun readStatePathStr(state: JSONObject, path: String): String {
            return readStatePath(state, path)?.toString() ?: ""
        }

        fun evaluateExprKotlin(expr: String, state: JSONObject): Any? {
            try {
                if (expr.contains("+ '_' +")) {
                    return evaluateConcatExpr(expr, state)
                }

                if (expr.contains("?") && expr.contains(":")) {
                    return evaluateTernaryExpr(expr, state)
                }

                val pathMatch = Regex("""^state\??\.(.+)$""").find(expr.trim())
                if (pathMatch != null) {
                    return readStatePath(state, expr.trim())
                }

                return null
            } catch (_: Exception) {
                return null
            }
        }

        private fun evaluateConcatExpr(expr: String, state: JSONObject): String {
            val parts = expr.split("+ '_' +").map { it.trim() }
            val values = parts.map { part ->
                val orMatch = Regex("""\((.+?)\s*\|\|\s*(\d+)\)""").find(part)
                if (orMatch != null) {
                    val pathVal = readStatePath(state, orMatch.groupValues[1].trim())
                    (pathVal as? Number)?.toInt()?.toString() ?: orMatch.groupValues[2]
                } else {
                    readStatePath(state, part)?.toString() ?: "0"
                }
            }
            return values.joinToString("_")
        }

        private fun evaluateTernaryExpr(expr: String, state: JSONObject): Any? {
            var work = expr.trim()
            // Unwrap outer parens around nested ternary false-branches: ((cond ? a : b))
            while (work.startsWith("(") && work.endsWith(")")) {
                val inner = work.substring(1, work.length - 1).trim()
                if (findTopLevelTernary(inner) >= 0 && inner.contains(":")) {
                    work = inner
                } else {
                    break
                }
            }

            val questionIdx = findTopLevelTernary(work)
            if (questionIdx <= 0) return null

            val condPart = work.substring(0, questionIdx).trim()
            val rest = work.substring(questionIdx + 1)
            val colonIdx = findTopLevelColon(rest)
            if (colonIdx <= 0) return null

            val truePart = rest.substring(0, colonIdx).trim()
            val falsePart = rest.substring(colonIdx + 1).trim()

            val condResult = evaluateConditionPart(condPart, state)
            val branch = if (condResult) truePart else falsePart
            return evaluateValuePart(branch, state)
        }

        private fun evaluateConditionPart(condExpr: String, state: JSONObject): Boolean {
            val trimmed = condExpr.trim()

            if (trimmed.startsWith("!")) {
                return !evaluateConditionPart(trimmed.removePrefix("!").trim(), state)
            }

            // Split by top-level || / && BEFORE checking .includes(), so that compound
            // conditions like "(pos || '').includes('x') || (pos || '').includes('y')" are
            // correctly split into two separate .includes() calls instead of being passed
            // whole to evaluateIncludes (which would mis-parse the combined receiver).
            val orParts = splitByTopLevelOp(trimmed, "||")
            if (orParts.size > 1) {
                return orParts.any { evaluateConditionPart(it.trim(), state) }
            }

            val andParts = splitByTopLevelOp(trimmed, "&&")
            if (andParts.size > 1) {
                return andParts.all { evaluateConditionPart(it.trim(), state) }
            }

            if (trimmed.contains(".includes(")) {
                return evaluateIncludes(trimmed, state)
            }

            if (trimmed.startsWith("state") || trimmed.startsWith("(state")) {
                val clean = trimmed.removePrefix("(").removeSuffix(")")
                val value = readStatePath(state, clean)
                return isTruthy(value)
            }

            return false
        }

        /** Splits [s] by [op] only at parenthesis depth 0, so operators inside `(...)` are not split. */
        private fun splitByTopLevelOp(s: String, op: String): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var start = 0
            var i = 0
            while (i < s.length) {
                when (s[i]) {
                    '(' -> { depth++; i++ }
                    ')' -> { depth--; i++ }
                    else -> {
                        if (depth == 0 && i + op.length <= s.length && s.startsWith(op, i)) {
                            parts.add(s.substring(start, i))
                            i += op.length
                            start = i
                        } else {
                            i++
                        }
                    }
                }
            }
            parts.add(s.substring(start))
            return parts
        }

        private fun evaluateIncludes(expr: String, state: JSONObject): Boolean {
            val trimmed = expr.trim()
            // (state?.path || '').includes('x') — match receiver up to .includes
            val m = Regex("""^(.+)\.includes\('(.+?)'\)$""").find(trimmed) ?: return false
            val receiver = m.groupValues[1].trim()
            val searchStr = m.groupValues[2]

            val orEmpty = Regex("""^\(\s*(.+?)\s*\|\|\s*''\s*\)$""").find(receiver)
            if (orEmpty != null) {
                val pathExpr = orEmpty.groupValues[1].trim()
                val value = readStatePathStr(state, pathExpr)
                return value.contains(searchStr, ignoreCase = true)
            }

            val value = readStatePathStr(state, receiver)
            return value.contains(searchStr, ignoreCase = true)
        }

        private fun evaluateValuePart(value: String, state: JSONObject): Any? {
            var trimmed = value.trim()
            while (trimmed.startsWith("(") && trimmed.endsWith(")")) {
                val inner = trimmed.substring(1, trimmed.length - 1).trim()
                if (findTopLevelTernary(inner) >= 0 && inner.contains(":")) {
                    trimmed = inner
                } else {
                    break
                }
            }

            trimmed.toDoubleOrNull()?.let { return it }
            trimmed.toIntOrNull()?.let { return it }

            if (trimmed == "true") return true
            if (trimmed == "false") return false

            if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                return trimmed.removeSurrounding("'")
            }

            if (trimmed.contains("?") && trimmed.contains(":")) {
                return evaluateTernaryExpr(trimmed, state)
            }

            if (trimmed.startsWith("state")) {
                return readStatePath(state, trimmed)
            }

            return trimmed.toDoubleOrNull() ?: trimmed.toIntOrNull() ?: null
        }

        private fun findTopLevelTernary(s: String): Int {
            var depth = 0
            for (i in s.indices) {
                when (s[i]) {
                    '(' -> depth++
                    ')' -> depth--
                    '?' -> if (depth == 0 && (i + 1 < s.length) && s[i + 1] != '.') return i
                }
            }
            return -1
        }

        private fun findTopLevelColon(s: String): Int {
            var depth = 0
            for (i in s.indices) {
                when (s[i]) {
                    '(' -> depth++
                    ')' -> depth--
                    ':' -> if (depth == 0) return i
                }
            }
            return -1
        }
    }
}
