package com.surveysparrow.surveysparrow_android_sdk

import android.util.Log
import java.util.UUID

suspend fun closeSpotCheck(config: SpotCheckConfig) {
    try {
        val apiService = RetrofitClient.create("https://${config.domainName}")
        val payload = DismissPayload(
            traceId = config.traceId,
            triggerToken = config.triggerToken
        )
        val response =
            apiService.closeSpotCheck(spotCheckContactID = String.format("%.0f", config.spotCheckContactID), payload = payload)
        if (response.success == true) {
            Log.i("SPOT-CHECK", "CloseSpotCheck: Success")
            config.spotCheckID = 0.0
            config.position = ""
            config.currentQuestionHeight = 0.0
            config.isCloseButtonEnabled = false
            config.closeButtonStyle = mapOf()
            config.spotCheckContactID = 0.0
            config.spotCheckURL = ""
        }
    } catch (e: Exception) {
        Log.i("SPOT-CHECK", "$e")
    }

}

fun generateTraceId(): String {
    val uuid = UUID.randomUUID()
    val timestamp = System.currentTimeMillis()
    return "$uuid-$timestamp"
}

fun isHexColor(color: String): Boolean {
    val hexColorPattern = "^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"
    return color.matches(hexColorPattern.toRegex())
}