package com.surveysparrow.surveysparrow_android_sdk.internal.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

internal object SpotCheckStore {
    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<JSONObject> = _state.asStateFlow()

    fun getState(): JSONObject = _state.value

    fun dispatch(update: JSONObject) {
        val current = JSONObject(_state.value.toString())
        val spotCheckState = current.getJSONObject("SpotCheckState")
        mergeState(spotCheckState, update)
        _state.value = current
    }

    fun reset() {
        _state.value = createInitialState()
    }

    private fun mergeState(target: JSONObject, source: JSONObject) {
        val nestedMergeKeys = setOf("params", "currentSpotcheck", "spotCheckDetails", "webViewDetails")

        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val sourceValue = source.get(key)
            if (key in nestedMergeKeys && sourceValue is JSONObject && target.optJSONObject(key) != null) {
                val existingObj = target.getJSONObject(key)
                val sourceObj = sourceValue
                val innerKeys = sourceObj.keys()
                while (innerKeys.hasNext()) {
                    val innerKey = innerKeys.next()
                    existingObj.put(innerKey, sourceObj.get(innerKey))
                }
            } else {
                target.put(key, sourceValue)
            }
        }
    }

    private fun createInitialState(): JSONObject {
        return JSONObject().apply {
            put("SpotCheckState", JSONObject().apply {
                put("allSpotChecksInToken", org.json.JSONArray())
                put("customEventsSpotChecks", org.json.JSONArray())
                put("filteredSpotChecks", org.json.JSONArray())
                put("showSpotCheck", false)
                put("currentSpotcheck", JSONObject())
                put("params", JSONObject().apply {
                    put("targetToken", "")
                    put("domainName", "")
                    put("userDetails", JSONObject())
                    put("variables", JSONObject())
                    put("customProperties", JSONObject())
                    put("visitor", JSONObject())
                    put("framework", "android")
                    put("userAgent", "")
                    put("traceId", "")
                })
                put("spotCheckDetails", JSONObject().apply {
                    put("keyBoardHeight", 0)
                    put("textPosition", 0)
                    put("isMounted", false)
                    put("isVisible", false)
                    put("isExiting", false)
                    put("isFullScreenMode", false)
                    put("showSurveyContent", true)
                    put("position", "")
                    put("mode", "")
                    put("spotCheckType", "")
                    put("currentQuestionHeight", 0)
                    put("miniCardHeight", 0)
                    put("avatarEnabled", false)
                    put("avatarUrl", "")
                    put("isBannerImageOn", false)
                    put("isSpotCheckButton", false)
                    put("spotCheckButtonConfig", JSONObject())
                    put("sideTabButtonWidth", 0)
                    put("closeButton", JSONObject().apply {
                        put("isEnabled", false)
                        put("color", "#000000")
                        put("isMiniCard", false)
                    })
                })
                put("webViewDetails", JSONObject().apply {
                    put("isChatEnabled", false)
                    put("isClassicEnabled", false)
                    put("chatUrl", "")
                    put("classicUrl", "")
                    put("canShowClassic", false)
                    put("canShowChat", false)
                    put("isClassicLoading", true)
                    put("isChatLoading", true)
                    put("isCurrentSpotcheckChat", JSONObject.NULL)
                    put("webViewInjectionData", JSONObject.NULL)
                    put("scrollEnabled", true)
                })
            })
        }
    }
}
