package com.surveysparrow.surveysparrow_android_sdk.internal.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal object SpotCheckApiClient {

    suspend fun fetchInitData(domainName: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://$domainName/api/internal/spotcheck/mobile/init?framework=android")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()
                    conn.disconnect()
                    JSONObject(response)
                } else {
                    Log.e("SpotCheckSDK", "Init API returned ${conn.responseCode}")
                    conn.disconnect()
                    null
                }
            } catch (e: Exception) {
                Log.e("SpotCheckSDK", "Failed to fetch init data", e)
                null
            }
        }
    }
}
