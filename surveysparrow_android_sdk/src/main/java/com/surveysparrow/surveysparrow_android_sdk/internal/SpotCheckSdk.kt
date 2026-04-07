package com.surveysparrow.surveysparrow_android_sdk.internal

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build

import com.surveysparrow.surveysparrow_android_sdk.SsSpotcheckListener
import com.surveysparrow.surveysparrow_android_sdk.internal.adapters.KeyboardAdapter
import com.surveysparrow.surveysparrow_android_sdk.internal.adapters.ListenerAdapter
import com.surveysparrow.surveysparrow_android_sdk.internal.adapters.SentryAdapter
import com.surveysparrow.surveysparrow_android_sdk.internal.adapters.StorageAdapter
import com.surveysparrow.surveysparrow_android_sdk.internal.api.SpotCheckApiClient
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.registerDefaultComponents
import com.surveysparrow.surveysparrow_android_sdk.internal.components.WebViewRefHolder
import com.surveysparrow.surveysparrow_android_sdk.internal.components.registerCloseButton
import com.surveysparrow.surveysparrow_android_sdk.internal.components.registerSpotCheckButton
import com.surveysparrow.surveysparrow_android_sdk.internal.components.registerWebViewRenderer
import com.surveysparrow.surveysparrow_android_sdk.internal.execute.ExecuteBridge
import com.surveysparrow.surveysparrow_android_sdk.internal.execute.JsEngine
import com.surveysparrow.surveysparrow_android_sdk.internal.state.ComponentStore
import com.surveysparrow.surveysparrow_android_sdk.internal.state.FunctionStore
import com.surveysparrow.surveysparrow_android_sdk.internal.state.SpotCheckStore
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class SpotCheckSdk private constructor() {

    lateinit var executeBridge: ExecuteBridge
        private set

    private lateinit var jsEngine: JsEngine
    private lateinit var storage: StorageAdapter
    private lateinit var listener: ListenerAdapter
    private lateinit var sentry: SentryAdapter
    private lateinit var keyboard: KeyboardAdapter

    private var isInitialized = false
    private val initDeferred = CompletableDeferred<Unit>()
    private var domainName: String = ""
    private var targetToken: String = ""

    suspend fun initialize(
        context: Context,
        domainName: String,
        targetToken: String,
        userDetails: Map<String, String>,
        variables: Map<String, Any>,
        customProperties: Map<String, Any>,
        preferences: SharedPreferences?,
        spotCheckListener: SsSpotcheckListener?,
    ) {
        if (isInitialized) return

        this.domainName = domainName
        this.targetToken = targetToken

        storage = StorageAdapter(preferences)
        listener = ListenerAdapter().apply { this.listener = spotCheckListener }
        sentry = SentryAdapter(domainName)
        keyboard = KeyboardAdapter()

        jsEngine = JsEngine(context, domainName)
        jsEngine.initialize()

        executeBridge = ExecuteBridge(jsEngine, storage, listener, sentry, keyboard)
        sentry.executeBridge = executeBridge::execute as suspend (String, JSONObject?) -> String?

        registerDefaultComponents()

        registerCloseButton(executeBridge)
        registerWebViewRenderer(executeBridge)
        registerSpotCheckButton(executeBridge)

        val initParams = JSONObject().apply {
            put("domainName", domainName)
            put("targetToken", targetToken)
            put("userDetails", JSONObject(userDetails))
            put("variables", JSONObject(variables))
            put("customProperties", JSONObject(customProperties))
            put("visitor", buildVisitorInfo())
            put("framework", "android")
            put("userAgent", buildUserAgent())
        }
        SpotCheckStore.dispatch(JSONObject().apply {
            put("params", initParams)
        })

        val response = SpotCheckApiClient.fetchInitData(domainName) ?: return


        val componentSchemas = response.optJSONObject("componentSchemas")
        if (componentSchemas != null) {
            ComponentStore.loadFromResponse(componentSchemas)
        }

        FunctionStore.loadFromResponse(response)

        val functionsJson = buildFunctionsJsonForEngine(response)
        jsEngine.storeFunctions(functionsJson)

        executeBridge.execute("initializeSpotcheckComponent", initParams)

        isInitialized = true
        initDeferred.complete(Unit)
    }

    private suspend fun awaitInit() {
        if (isInitialized) return
        initDeferred.await()
    }

    fun setActivity(activity: Activity?) {
        if (::keyboard.isInitialized) {
            keyboard.setActivity(activity)
        }
    }

    suspend fun trackScreen(
        screenName: String,
        options: Map<String, Any> = emptyMap(),
    ) {
        awaitInit()
        val params = JSONObject().apply {
            put("screen", screenName)
            if (options.isNotEmpty()) {
                put("options", JSONObject(options))
            }
        }
        executeBridge.execute("trackScreen", params)
    }

    suspend fun trackEvent(screenName: String, event: Map<String, Any>) {
        awaitInit()
        executeBridge.execute(
            "trackEvent",
            JSONObject().apply {
                put("screen", screenName)
                put("event", JSONObject(event))
            }
        )
    }

    fun injectUnmountApp() {
        if (!isInitialized) return
        val webViewDetails = SpotCheckStore.getState()
            .optJSONObject("SpotCheckState")
            ?.optJSONObject("webViewDetails")
        val isChat = webViewDetails?.opt("isCurrentSpotcheckChat")?.let {
            it != org.json.JSONObject.NULL && it == true
        } ?: false
        WebViewRefHolder.injectUnmountApp(isChat)
    }

    suspend fun handleNavigationChange() {
        awaitInit()
        injectUnmountApp()
        executeBridge.execute("handleNavigationChange")
    }

    suspend fun closeSpotCheck() {
        awaitInit()
        injectUnmountApp()
        executeBridge.execute("closeButton.handleCloseButton")
    }

    fun destroy() {
        jsEngine.destroy()
        SpotCheckStore.reset()
        FunctionStore.reset()
        ComponentStore.reset()
        isInitialized = false
    }

    private fun buildVisitorInfo(): JSONObject {
        val dm = Resources.getSystem().displayMetrics
        return JSONObject().apply {
            put("deviceType", "MOBILE")
            put("operatingSystem", "Android")
            put("screenResolution", JSONObject().apply {
                put("width", dm.widthPixels)
                put("height", dm.heightPixels)
            })
            put("currentDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("timezone", TimeZone.getDefault().id)
        }
    }

    private fun buildUserAgent(): String {
        val dm = Resources.getSystem().displayMetrics
        val dpr = dm.density.toInt()
        val isTablet = when {
            dpr < 2 && (dm.widthPixels >= 1000 || dm.heightPixels >= 1000) -> true
            dpr == 2 && (dm.widthPixels >= 1920 || dm.heightPixels >= 1920) -> true
            else -> false
        }
        val deviceType = if (isTablet) "Tablet" else "Mobile"
        return "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; $deviceType) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36"
    }

    private fun buildFunctionsJsonForEngine(response: JSONObject): String {
        val functions = JSONObject()

        val topLevel = listOf("initializeSpotcheckComponent", "trackScreen", "trackEvent", "handleNavigationChange", "handleExitAnimationComplete")
        for (name in topLevel) {
            response.optString(name, "").takeIf { it.isNotEmpty() }?.let {
                functions.put(name, it)
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
            val jsGroup = JSONObject()
            for (funcName in funcNames) {
                groupObj.optString(funcName, "").takeIf { it.isNotEmpty() }?.let {
                    jsGroup.put(funcName, it)
                }
            }
            functions.put(groupName, jsGroup)
        }

        return functions.toString()
    }

    companion object {
        val instance: SpotCheckSdk by lazy { SpotCheckSdk() }
    }
}
