package com.surveysparrow.surveysparrow_android_sdk.internal.state

import org.json.JSONObject

internal object ComponentStore {
    @Volatile
    var isLoaded: Boolean = false
        private set

    var wrapper: JSONObject? = null
        private set
    var closeButton: JSONObject? = null
        private set
    var webviewComponent: JSONObject? = null
        private set
    var spotCheckButton: JSONObject? = null
        private set

    fun loadFromResponse(schemas: JSONObject) {

        wrapper = schemas.optJSONObject("wrapper")
        closeButton = schemas.optJSONObject("closeButton")
        webviewComponent = schemas.optJSONObject("webviewComponent")
        spotCheckButton = schemas.optJSONObject("spotCheckButton")
        isLoaded = true
    }

    fun reset() {
        wrapper = null
        closeButton = null
        webviewComponent = null
        spotCheckButton = null
        isLoaded = false
    }
}
