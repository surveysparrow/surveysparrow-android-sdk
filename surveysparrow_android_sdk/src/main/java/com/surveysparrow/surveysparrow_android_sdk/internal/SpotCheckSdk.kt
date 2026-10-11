package com.surveysparrow.surveysparrow_android_sdk.internal

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

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
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.GenericActions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class SpotCheckSdk private constructor() {

    lateinit var executeBridge: ExecuteBridge
        private set

    private lateinit var jsEngine: JsEngine
    private lateinit var storage: StorageAdapter
    private lateinit var listener: ListenerAdapter
    private lateinit var sentry: SentryAdapter
    private lateinit var keyboard: KeyboardAdapter

    @Volatile
    private var isInitialized = false
    @Volatile
    private var initDeferred = CompletableDeferred<Unit>()
    // True when the last init attempt failed and no new one has started.
    @Volatile
    private var initFailed = false
    val hasInitFailed: Boolean get() = initFailed
    private val initMutex = kotlinx.coroutines.sync.Mutex()
    private var domainName: String = ""

    /** Backend page script from init config; null keeps the built-in one. */
    @Volatile
    var remoteWebViewScript: String? = null
        private set
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
    ) = initMutex.withLock {
        if (isInitialized) {
            if (this.domainName == domainName && this.targetToken == targetToken) {
                // Legacy: each config had its own details, listener and traceId.
                listener.listener = spotCheckListener
                storage.preferences = preferences
                SpotCheckStore.dispatch(JSONObject().put("params", JSONObject().apply {
                    put("userDetails", JSONObject(userDetails))
                    put("variables", JSONObject(variables))
                    put("customProperties", JSONObject(customProperties))
                    // Keep the open survey's traceId (config recreated on rotation).
                    if (!isSurveyOpen()) put("traceId", "")
                }))
                return@withLock
            }
            // Different account config: start over.
            destroy()
        }

        initFailed = false
        try {
            this.domainName = domainName
            this.targetToken = targetToken

            // Previous failed attempt's Sentry hub (its report has already gone out).
            if (::sentry.isInitialized) sentry.close()
            storage = StorageAdapter(preferences)
            listener = ListenerAdapter().apply { this.listener = spotCheckListener }
            sentry = SentryAdapter(domainName, context.applicationContext)
            keyboard = KeyboardAdapter()

            jsEngine = JsEngine(context.applicationContext, domainName)
            jsEngine.initialize()

            executeBridge = ExecuteBridge(jsEngine, storage, listener, sentry, keyboard)
            jsEngine.onWebViewInject = { target, script ->
                if (target == "chat") WebViewRefHolder.evaluateJsOnChat(script)
                else WebViewRefHolder.evaluateJsOnClassic(script)
            }
            sentry.executeBridge = executeBridge::execute as suspend (String, JSONObject?) -> String?
            GenericActions.runner = { fn, params ->
                CoroutineScope(Dispatchers.IO).launch { executeBridge.execute(fn, params) }
            }

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
                put("sdkVersion", JsEngine.SDK_VERSION)
                put("userAgent", buildUserAgent())
                put("device", buildDeviceFacts(context.applicationContext))
            }
            SpotCheckStore.dispatch(JSONObject().apply {
                put("params", initParams)
            })

            val response = SpotCheckApiClient.fetchInitData(domainName)
                ?: throw IllegalStateException("SpotCheck init failed: mobile/init returned null or non-OK response")

            val componentSchemas = response.optJSONObject("componentSchemas")
            if (componentSchemas != null) {
                ComponentStore.loadFromResponse(componentSchemas)
            }

            FunctionStore.loadFromResponse(response)
            remoteWebViewScript = response.optJSONObject("config")
                ?.optString("webViewScript", "")
                ?.takeIf { it.isNotBlank() }

            val functionsJson = buildFunctionsJsonForEngine(response)
            jsEngine.storeFunctions(functionsJson)

            executeBridge.execute("initializeSpotcheckComponent", initParams)

            isInitialized = true
            initDeferred.complete(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancelled (rotation/navigation): waiters keep waiting for the next initialize().
            releaseFailedInit()
            throw e
        } catch (e: Exception) {
            if (::sentry.isInitialized) {
                sentry.captureP0Error(e.message ?: "Initialization failed", "SPOTCHECK_INITIALIZATION", mapOf("action" to "initialize"), e)
            }
            failInit(e)
            throw e
        }
    }

    // Frees the hidden WebView, fails waiters and lets the next initialize() retry.
    private fun failInit(e: Exception) {
        releaseFailedInit()
        initFailed = true
        if (!initDeferred.isCompleted) {
            initDeferred.completeExceptionally(e)
        }
        initDeferred = CompletableDeferred()
    }

    private fun releaseFailedInit() {
        if (::jsEngine.isInitialized) jsEngine.destroy()
        GenericActions.runner = null
    }

    /** False when initialization failed; callers skip instead of crashing the host app. */
    private suspend fun awaitInit(): Boolean {
        if (isInitialized) return true
        // Read the deferred before the flag so a failure in between is not missed.
        val deferred = initDeferred
        if (initFailed) return false
        return try {
            deferred.await()
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    // Per-screen SpotCheck: the next screen mounts before the previous one is disposed.
    private val liveSpotChecks = AtomicInteger(0)
    private val latestSpotCheckId = AtomicLong(0)
    private val pendingNavigationClose = AtomicBoolean(false)

    fun onSpotCheckMounted(): Long {
        if (liveSpotChecks.getAndIncrement() > 0) pendingNavigationClose.set(true)
        return latestSpotCheckId.incrementAndGet()
    }

    // True when this dispose must run the navigation close itself.
    fun onSpotCheckDisposed(id: Long): Boolean {
        val remaining = liveSpotChecks.decrementAndGet()
        if (id == latestSpotCheckId.get() || remaining <= 0) {
            pendingNavigationClose.set(false)
            return true
        }
        // A newer screen is up: close only if its track call hasn't already done it.
        return pendingNavigationClose.getAndSet(false)
    }

    // Close the previous screen's spotcheck before the new screen's call (never after it).
    private suspend fun runPendingNavigationClose() {
        if (pendingNavigationClose.getAndSet(false)) handleNavigationChange()
    }

    // Screen whose open survey must survive an Activity recreation (rotation).
    @Volatile
    private var rotationKeepScreen: String? = null

    fun setActivity(activity: Activity?) {
        if (::keyboard.isInitialized) {
            val changed = keyboard.setActivity(activity)
            // New window after rotation: re-apply the survey keyboard mode.
            if (changed && isSurveyOpen()) keyboard.pauseDefaultKeyboardBehavior()
        }
    }

    private fun isSurveyOpen(): Boolean {
        val details = SpotCheckStore.state.value.optJSONObject("SpotCheckState")
            ?.optJSONObject("spotCheckDetails") ?: return false
        return details.optBoolean("isVisible", false) && details.optBoolean("showSurveyContent", false)
    }

    // Activity recreated (rotation): keep the spotcheck instead of the navigation close.
    fun onActivityRecreating() {
        if (!isInitialized || !isSurveyOpen()) return
        rotationKeepScreen = SpotCheckStore.state.value.optJSONObject("SpotCheckState")
            ?.optJSONObject("currentSpotcheck")?.optString("screenName", "")
            ?.takeIf { it.isNotEmpty() }
    }

    suspend fun trackScreen(
        screenName: String,
        options: Map<String, Any> = emptyMap(),
    ) {
        if (!awaitInit()) return
        runPendingNavigationClose()
        val keepScreen = rotationKeepScreen
        if (keepScreen != null) {
            // The app re-tracks its screen after rotation; keep the open survey.
            if (keepScreen == screenName && isSurveyOpen()) return
            rotationKeepScreen = null
        }
        sentry.addBreadcrumb("trackScreen", mapOf("screen" to screenName))
        val params = JSONObject().apply {
            put("screen", screenName)
            if (options.isNotEmpty()) {
                put("options", JSONObject(options))
            }
        }
        executeBridge.execute("trackScreen", params)
    }

    suspend fun trackEvent(screenName: String, event: Map<String, Any>) {
        if (!awaitInit()) return
        runPendingNavigationClose()
        sentry.addBreadcrumb("trackEvent", mapOf("screen" to screenName, "event" to event.keys.joinToString(",")))
        executeBridge.execute(
            "trackEvent",
            JSONObject().apply {
                put("screen", screenName)
                put("event", JSONObject(event))
            }
        )
    }

    suspend fun handleNavigationChange() {
        if (!awaitInit()) return
        rotationKeepScreen = null
        executeBridge.execute("handleNavigationChange")
    }

    // Public close: same as legacy (no onCloseButtonTap).
    suspend fun closeSpotCheck() {
        if (!awaitInit()) return
        rotationKeepScreen = null
        executeBridge.execute("handleNavigationChange", JSONObject().put("reason", "close"))
    }

    fun destroy() {
        if (!initDeferred.isCompleted) {
            initDeferred.completeExceptionally(
                IllegalStateException("SpotCheckSdk destroyed before initialization completed"),
            )
        }
        initDeferred = CompletableDeferred()
        if (::jsEngine.isInitialized) {
            jsEngine.destroy()
        }
        if (::sentry.isInitialized) {
            sentry.close()
        }
        rotationKeepScreen = null
        GenericActions.runner = null
        // Same-domain WebViews must reload for the new account.
        WebViewRefHolder.classicWebView = null
        WebViewRefHolder.chatWebView = null
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
            put("currentDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            put("timezone", TimeZone.getDefault().id)
        }
    }

    // Raw device facts; the backend builds the User-Agent from these.
    private fun buildDeviceFacts(appContext: Context): JSONObject {
        val dm = Resources.getSystem().displayMetrics
        val dpr = dm.density.toInt()
        val isTablet = (dpr < 2 && (dm.widthPixels >= 1000 || dm.heightPixels >= 1000)) ||
            (dpr == 2 && (dm.widthPixels >= 1920 || dm.heightPixels >= 1920))
        return JSONObject().apply {
            put("os", "android")
            put("osVersion", Build.VERSION.RELEASE)
            put("isTablet", isTablet)
            // Host app release; the backend keeps the SDK version of the newest build.
            try {
                val pkg = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                pkg.versionName?.takeIf { it.isNotEmpty() }?.let { put("appVersion", it) }
                put("appBuild", PackageInfoCompat.getLongVersionCode(pkg).toString())
            } catch (_: Throwable) {
            }
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

    // Same rule as FunctionStore: every string, or object of strings, except non-function keys.
    private fun buildFunctionsJsonForEngine(response: JSONObject): String {
        val functions = JSONObject()
        for (key in response.keys()) {
            if (key in FunctionStore.NON_FUNCTION_KEYS) continue
            when (val value = response.opt(key)) {
                is String -> if (value.isNotEmpty()) functions.put(key, value)
                is JSONObject -> {
                    val jsGroup = JSONObject()
                    for (name in value.keys()) {
                        val fn = value.opt(name)
                        if (fn is String && fn.isNotEmpty()) jsGroup.put(name, fn)
                    }
                    if (jsGroup.length() > 0) functions.put(key, jsGroup)
                }
            }
        }
        return functions.toString()
    }

    companion object {
        val instance: SpotCheckSdk by lazy { SpotCheckSdk() }
    }
}
