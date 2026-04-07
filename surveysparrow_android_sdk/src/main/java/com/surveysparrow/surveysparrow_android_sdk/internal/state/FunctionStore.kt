package com.surveysparrow.surveysparrow_android_sdk.internal.state

import org.json.JSONObject

internal object FunctionStore {
    @Volatile
    var isLoaded: Boolean = false
        private set

    private val functions = mutableMapOf<String, Any>()

    fun loadFromResponse(response: JSONObject) {
        val topLevelKeys = listOf(
            "initializeSpotcheckComponent", "trackScreen", "trackEvent",
            "handleNavigationChange", "handleExitAnimationComplete"
        )
        for (key in topLevelKeys) {
            response.optString(key, "").takeIf { it.isNotEmpty() }?.let {
                functions[key] = it
            }
        }

        val groups = mapOf(
            "webviewComponent" to listOf("handleWebViewMessage", "handleWebViewError", "handleWebViewInjection", "classicWebViewRefCallback", "chatWebViewRefCallback"),
            "closeButton" to listOf("handleCloseButton", "getCloseButtonStyles"),
            "wrapper" to listOf("getWrapperStyles"),
            "spotCheckButton" to listOf("handleSpotCheckButtonPress", "handleSideTabLayout", "getSpotCheckButtonStyles"),
            "sentry" to listOf("processSentryError"),
        )

        for ((groupName, funcNames) in groups) {
            val groupObj = response.optJSONObject(groupName) ?: continue
            val groupMap = mutableMapOf<String, String>()
            for (funcName in funcNames) {
                groupObj.optString(funcName, "").takeIf { it.isNotEmpty() }?.let {
                    groupMap[funcName] = it
                }
            }
            functions[groupName] = groupMap
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
