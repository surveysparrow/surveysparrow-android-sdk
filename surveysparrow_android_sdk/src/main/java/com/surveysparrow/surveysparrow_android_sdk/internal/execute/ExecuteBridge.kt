package com.surveysparrow.surveysparrow_android_sdk.internal.execute

import android.util.Log
import com.surveysparrow.surveysparrow_android_sdk.internal.adapters.KeyboardAdapter
import com.surveysparrow.surveysparrow_android_sdk.internal.adapters.ListenerAdapter
import com.surveysparrow.surveysparrow_android_sdk.internal.adapters.SentryAdapter
import com.surveysparrow.surveysparrow_android_sdk.internal.adapters.StorageAdapter
import com.surveysparrow.surveysparrow_android_sdk.internal.state.FunctionStore
import com.surveysparrow.surveysparrow_android_sdk.internal.state.SpotCheckStore
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal class ExecuteBridge(
    private val jsEngine: JsEngine,
    private val storage: StorageAdapter,
    private val listener: ListenerAdapter,
    private val sentry: SentryAdapter,
    private val keyboard: KeyboardAdapter,
) {
    init {
        wireCallbacks()
    }

    private fun wireCallbacks() {
        jsEngine.onDispatch = { updateJson ->
            try {
                val update = JSONObject(updateJson)
                SpotCheckStore.dispatch(update)
            } catch (e: Exception) {
                Log.e("SpotCheckSDK", "dispatchWrapper error", e)
            }
        }

        jsEngine.onSaveData = { data, callbackId ->
            storage.saveData(data)
            jsEngine.resolveCallback(callbackId, "")
        }

        jsEngine.onLoadData = { isTraceId, callbackId ->
            val result = storage.loadData(isTraceId)
            jsEngine.resolveCallback(callbackId, result)
        }

        jsEngine.onSurveyLoaded = { responseJson ->
            try {
                val map = jsonToMap(JSONObject(responseJson))
                listener.onSurveyLoaded(map)
            } catch (_: Exception) {}
        }

        jsEngine.onSurveyResponse = { responseJson ->
            try {
                val map = jsonToMap(JSONObject(responseJson))
                listener.onSurveyResponse(map)
            } catch (_: Exception) {}
        }

        jsEngine.onPartialSubmission = { responseJson ->
            try {
                val map = jsonToMap(JSONObject(responseJson))
                listener.onPartialSubmission(map)
            } catch (_: Exception) {}
        }

        jsEngine.onCloseButtonTap = {
            listener.onCloseButtonTap()
        }

        jsEngine.onCaptureError = { errorJson ->
            try {
                val obj = JSONObject(errorJson)
                val priority = obj.optString("priority", "P1")
                val error = obj.optString("error", "Unknown")
                val source = obj.optString("source", "GENERAL")
                val contextObj = obj.optJSONObject("context")
                val context = if (contextObj != null) jsonToMap(contextObj) else emptyMap()
                if (priority == "P0") {
                    sentry.captureP0Error(error, source, context)
                } else {
                    sentry.captureP1Error(error, source, context)
                }
            } catch (_: Exception) {}
        }

        jsEngine.onPauseKeyboard = {
            keyboard.pauseDefaultKeyboardBehavior()
        }

        jsEngine.onResumeKeyboard = {
            keyboard.resumeDefaultKeyboardBehavior()
        }

        jsEngine.onExecuteError = { functionName, error ->
            if (!isSentryFunction(functionName)) {
                Log.e("SpotCheckSDK", "[execute] $functionName: $error")
                sentry.captureP1Error(error, "GENERAL", mapOf("action" to "execute:runtime", "functionName" to functionName))
            }
        }
    }

    // Reporting failures are not reported again (avoids loops).
    private fun isSentryFunction(functionName: String) = functionName.startsWith("sentry.")

    // Style getters run on every recomposition; they would push useful breadcrumbs out.
    private fun isBreadcrumbWorthy(functionName: String) =
        !isSentryFunction(functionName) && !STYLE_GETTER.containsMatchIn(functionName)

    // Message type only; never survey content.
    private fun describeParams(functionName: String, params: JSONObject?): Map<String, Any?> {
        if (functionName != "webviewComponent.handleWebViewMessage") return emptyMap()
        val data = params?.optJSONObject("event")?.optJSONObject("nativeEvent")?.optString("data") ?: return emptyMap()
        if (data == "captureImage") return mapOf("messageType" to data)
        return try {
            mapOf("messageType" to JSONObject(data).optString("type"))
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private companion object {
        val STYLE_GETTER = Regex("\\.get\\w*Styles$")
    }

    suspend fun execute(functionName: String, params: JSONObject? = null): String? {
        if (!FunctionStore.isLoaded) return null

        try {
            if (isBreadcrumbWorthy(functionName)) {
                sentry.addBreadcrumb("execute", mapOf("functionName" to functionName) + describeParams(functionName, params))
            }
            val paramsJson = (params ?: JSONObject()).toString()
            val stateJson = SpotCheckStore.getState().toString()
            return jsEngine.execute(functionName, paramsJson, stateJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isSentryFunction(functionName)) return null
            sentry.captureP1Error(
                e.message ?: "Unknown error",
                "GENERAL",
                mapOf("action" to "execute:setup", "functionName" to functionName)
            )
            return null
        }
    }

    // Same value types as legacy Gson: numbers as Double, JSON null as null.
    @Suppress("UNCHECKED_CAST")
    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = gsonValue(json.get(key))
        }
        return map as Map<String, Any>
    }

    private fun jsonArrayToList(array: org.json.JSONArray): List<Any?> =
        (0 until array.length()).map { gsonValue(array.get(it)) }

    private fun gsonValue(value: Any?): Any? = when (value) {
        is JSONObject -> jsonToMap(value)
        is org.json.JSONArray -> jsonArrayToList(value)
        JSONObject.NULL -> null
        is Number -> value.toDouble()
        else -> value
    }
}
