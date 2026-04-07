package com.surveysparrow.surveysparrow_android_sdk.internal.adapters

import android.content.SharedPreferences
import java.util.UUID

internal class StorageAdapter(private val preferences: SharedPreferences?) {

    fun saveData(value: String) {
        preferences?.edit()?.putString(STORAGE_KEY, value)?.apply()
    }

    fun loadData(isTraceId: Boolean): String {
        return if (isTraceId) {
            generateTraceId()
        } else {
            preferences?.getString(STORAGE_KEY, "") ?: ""
        }
    }

    private fun generateTraceId(): String {
        return "${UUID.randomUUID()}-${System.currentTimeMillis()}"
    }

    companion object {
        private const val STORAGE_KEY = "SurveySparrowUUID"
    }
}
