package com.surveysparrow.surveysparrow_android_sdk

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.surveysparrow.surveysparrow_android_sdk.internal.SpotCheckSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpotCheckConfig(
    var domainName: String,
    var targetToken: String,
    private var userDetails: HashMap<String, String>,
    private var variables: Map<String, Any> = mapOf(),
    private var customProperties: Map<String, Any> = mapOf(),
    var preferences: SharedPreferences? = null,
    var spotCheckListener: SsSpotcheckListener? = null
) {
    var position by mutableStateOf("bottom")
    var isVisible by mutableStateOf(false)
    var isFullScreenMode by mutableStateOf(false)
    var currentQuestionHeight by mutableDoubleStateOf(0.0)
    var isCloseButtonEnabled by mutableStateOf(false)
    var spotChecksMode by mutableStateOf("")
    var spotCheckType by mutableStateOf("")
    var isMounted by mutableStateOf(false)
    var showSurveyContent by mutableStateOf(true)
    var isSpotCheckButton by mutableStateOf(false)
    var spotCheckButtonConfig by mutableStateOf<Map<String, Any>>(mapOf())
    var activity: Activity? = null
    var originalSoftInputMode by mutableStateOf<Int?>(null)

    /** Reused across spotcheck sessions (old arch pattern — do not clear on dispose). */
    var classicWebViewRef: WebView? = null
    var chatWebViewRef: WebView? = null

    internal var isNewArchInitialized by mutableStateOf(false)

    internal suspend fun ensureInitialized(context: Context) {
        if (isNewArchInitialized) return
        SpotCheckSdk.instance.initialize(
            context = context,
            domainName = domainName,
            targetToken = targetToken,
            userDetails = userDetails,
            variables = variables,
            customProperties = customProperties,
            preferences = preferences,
            spotCheckListener = spotCheckListener,
        )
        isNewArchInitialized = true
    }

    internal suspend fun internalTrackScreen(
        screen: String,
        options: Map<String, Any> = emptyMap(),
    ) {
        SpotCheckSdk.instance.trackScreen(screen, options)
    }

    internal suspend fun internalTrackEvent(screen: String, event: Map<String, Any>) {
        SpotCheckSdk.instance.trackEvent(screen, event)
    }

    internal suspend fun handleNavigationChange() {
        SpotCheckSdk.instance.handleNavigationChange()
    }

    fun onClose(isNavigation: Boolean = false) {
        isVisible = false
        isFullScreenMode = false
        currentQuestionHeight = 0.0
        isCloseButtonEnabled = false
        isMounted = false
        spotChecksMode = ""
        isSpotCheckButton = false
        showSurveyContent = true

        activity?.runOnUiThread {
            originalSoftInputMode?.let {
                activity?.window?.setSoftInputMode(it)
            }
        }
    }

    fun openSpot() {
        activity?.runOnUiThread {
            activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        isVisible = true
    }
}
