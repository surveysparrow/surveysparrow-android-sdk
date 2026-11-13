package com.surveysparrow.surveysparrow_android_sdk.internal.adapters

import android.content.Context
import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.Hub
import io.sentry.SentryEvent
import io.sentry.android.core.DeviceInfoUtil
import io.sentry.android.core.SentryAndroidOptions
import android.os.Build
import io.sentry.protocol.App
import io.sentry.protocol.Device
import io.sentry.protocol.OperatingSystem
import org.json.JSONObject
import java.io.StringWriter

/**
 * Only loaded when the host app ships Sentry (compileOnly dependency).
 * Expo pattern: isolated Hub with a dummy DSN, never Sentry.init, no integrations
 * or crash handlers; beforeSend hands the enriched event to us and returns null.
 */
internal class SentryEnricher(
    context: Context,
    private val onEvent: (event: JSONObject, hintData: Map<String, Any?>) -> Unit,
) {
    private val appContext = context.applicationContext
    private val options = SentryAndroidOptions().apply {
        dsn = "https://dummy@sentry.io/0"
        isSendDefaultPii = false
        isEnableExternalConfiguration = false
        beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, hint ->
            @Suppress("UNCHECKED_CAST")
            val data = (hint.get(HINT_KEY) as? Map<String, Any?>) ?: emptyMap()
            val json = try {
                serialize(event)
            } catch (_: Throwable) {
                minimalEvent(event)
            }
            try {
                onEvent(json, data)
            } catch (_: Throwable) {
            }
            null
        }
    }
    private val hub: Hub

    init {
        options.addEventProcessor(DeviceContextProcessor())
        hub = Hub(options)
    }

    // False when the hub is closed, so the caller sends the basic report.
    fun capture(throwable: Throwable, hintData: Map<String, Any?>): Boolean {
        if (!hub.isEnabled) return false
        val hint = Hint().apply { set(HINT_KEY, hintData) }
        hub.captureEvent(SentryEvent(throwable), hint)
        return true
    }

    fun close() {
        hub.close()
    }

    // Fallback when serialization fails: keep the error type and message.
    private fun minimalEvent(event: SentryEvent): JSONObject {
        val t = event.throwable
        val value = JSONObject()
            .put("type", t?.javaClass?.simpleName ?: "Error")
            .put("value", t?.message ?: "Unknown error")
        return JSONObject().put("exception", JSONObject().put("values", org.json.JSONArray().put(value)))
    }

    private fun serialize(event: SentryEvent): JSONObject {
        val writer = StringWriter()
        options.serializer.serialize(event, writer)
        return JSONObject(writer.toString())
    }

    // Sentry's own collectors for device, OS and app context.
    private inner class DeviceContextProcessor : EventProcessor {
        override fun process(event: SentryEvent, hint: Hint): SentryEvent {
            try {
                // DeviceInfoUtil is a process-wide singleton; only use it once the host's Sentry built it.
                if (io.sentry.Sentry.isEnabled()) {
                    val info = DeviceInfoUtil.getInstance(appContext, options)
                    event.contexts.setDevice(info.collectDeviceInformation(false, false))
                    event.contexts.setOperatingSystem(info.operatingSystem)
                } else {
                    event.contexts.setDevice(Device().apply {
                        manufacturer = Build.MANUFACTURER
                        brand = Build.BRAND
                        model = Build.MODEL
                        family = Build.MODEL?.split(" ")?.firstOrNull()
                    })
                    event.contexts.setOperatingSystem(OperatingSystem().apply {
                        name = "Android"
                        version = Build.VERSION.RELEASE
                        build = Build.DISPLAY
                    })
                }
                val pm = appContext.packageManager
                val pkg = pm.getPackageInfo(appContext.packageName, 0)
                event.contexts.setApp(App().apply {
                    appIdentifier = appContext.packageName
                    appName = pm.getApplicationLabel(appContext.applicationInfo).toString()
                    appVersion = pkg.versionName
                })
            } catch (_: Throwable) {
            }
            return event
        }
    }

    companion object {
        private const val HINT_KEY = "spotcheck"
    }
}
