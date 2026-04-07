package com.surveysparrow.surveysparrow_android_sdk

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.surveysparrow.surveysparrow_android_sdk.internal.SpotCheckSdk
import com.surveysparrow.surveysparrow_android_sdk.internal.components.SpotCheckButtonRoot
import com.surveysparrow.surveysparrow_android_sdk.internal.components.WebViewContent
import com.surveysparrow.surveysparrow_android_sdk.internal.components.WrapperComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SpotCheck(config: SpotCheckConfig) {
    var hasError by remember { mutableStateOf(false) }

    if (hasError) return

    SpotCheckSafe(
        config = config,
        onError = { hasError = true },
    )
}

@Composable
private fun SpotCheckSafe(
    config: SpotCheckConfig,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val sdk = SpotCheckSdk.instance

    LaunchedEffect(Unit) {
        try {
            config.ensureInitialized(context)
        } catch (e: Exception) {
            Log.e("SpotCheckSDK", "Initialization failed", e)
            onError()
        }
    }

    val activity = remember(context) { extractActivity(context) }
    config.activity = activity
    sdk.setActivity(activity)

    if (config.originalSoftInputMode == null) {
        config.originalSoftInputMode = activity?.window?.attributes?.softInputMode
    }

    DisposableEffect(config) {
        onDispose {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    config.handleNavigationChange()
                } catch (_: Exception) {
                }
            }
        }
    }

    if (!config.isNewArchInitialized) return

    val executeBridge = sdk.executeBridge

    CompositionLocalProvider(LocalSpotCheckConfig provides config) {
        WrapperComponent(executeBridge = executeBridge) {
            WebViewContent(executeBridge = executeBridge)
        }

        SpotCheckButtonRoot(executeBridge = executeBridge)
    }
}

/** Provides SpotCheckConfig to WebViewRenderer for ref reuse (old arch pattern). */
internal val LocalSpotCheckConfig = compositionLocalOf<SpotCheckConfig?> { null }

private fun extractActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Track a screen name for spotcheck trigger evaluation.
 * Call this in LaunchedEffect when a screen becomes visible.
 */
suspend fun trackScreen(
    screen: String,
    config: SpotCheckConfig,
    options: Map<String, Any> = emptyMap(),
) {
    config.internalTrackScreen(screen, options)
}

/**
 * Close all active spotchecks.
 */
suspend fun closeSpotchecks(config: SpotCheckConfig) {
    SpotCheckSdk.instance.closeSpotCheck()
    config.onClose(true)
}

/**
 * Track a custom event for spotcheck trigger evaluation.
 */
suspend fun trackEvent(screen: String, event: Map<String, Any>, config: SpotCheckConfig) {
    config.internalTrackEvent(screen, event)
}

/**
 * Runs the bundled backend helper `handleNavigationChange`.
 * [SpotCheck] also invokes this via [DisposableEffect] when the composable leaves composition (e.g. navigation away).
 * Call from your app too if you need an extra signal (e.g. non-Compose navigation).
 */
suspend fun onSpotcheckNavigationChange(config: SpotCheckConfig) {
    config.handleNavigationChange()
}
