package com.surveysparrow.surveysparrow_android_sdk

import android.util.Log
import java.util.UUID



fun generateTraceId(): String {
    val uuid = UUID.randomUUID()
    val timestamp = System.currentTimeMillis()
    return "$uuid-$timestamp"
}

fun isHexColor(color: String): Boolean {
    val hexColorPattern = "^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"
    return color.matches(hexColorPattern.toRegex())
}