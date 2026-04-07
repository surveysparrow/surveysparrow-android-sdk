package com.surveysparrow.surveysparrow_android_sdk.internal.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.BuilderContext
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.BuilderNode
import com.surveysparrow.surveysparrow_android_sdk.internal.execute.ExecuteBridge
import com.surveysparrow.surveysparrow_android_sdk.internal.state.ComponentStore
import com.surveysparrow.surveysparrow_android_sdk.internal.state.SpotCheckStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun WrapperComponent(
    executeBridge: ExecuteBridge,
    children: @Composable () -> Unit,
) {
    val state by SpotCheckStore.state.collectAsState()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp
    val screenWidth = configuration.screenWidthDp

    var styles by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    val spotCheckState = state.optJSONObject("SpotCheckState") ?: JSONObject()
    val showSpotCheck = spotCheckState.optBoolean("showSpotCheck", false)
    val spotCheckDetails = spotCheckState.optJSONObject("spotCheckDetails") ?: JSONObject()
    val isMounted = spotCheckDetails.optBoolean("isMounted", false)
    val isVisible = spotCheckDetails.optBoolean("isVisible", false)
    val isExiting = spotCheckDetails.opt("isExiting")?.let {
        it != false && it != JSONObject.NULL && it.toString().isNotEmpty() && it.toString() != "false"
    } ?: false
    val currentQuestionHeight = spotCheckDetails.optInt("currentQuestionHeight", 0)
    val miniCardHeight = spotCheckDetails.optInt("miniCardHeight", 0)
    val keyBoardHeight = spotCheckDetails.optInt("keyBoardHeight", 0)
    val textPosition = spotCheckDetails.optInt("textPosition", 0)
    val mode = spotCheckDetails.optString("mode", "")
    val isFullScreenMode = spotCheckDetails.optBoolean("isFullScreenMode", false)

    LaunchedEffect(
        screenHeight, screenWidth, showSpotCheck,
        isMounted, isVisible, mode, isFullScreenMode,
        currentQuestionHeight, miniCardHeight,
        isExiting, keyBoardHeight, textPosition,
    ) {
        val params = JSONObject().apply {
            put("screenHeight", screenHeight)
            put("screenWidth", screenWidth)
        }
        val result = executeBridge.execute("wrapper.getWrapperStyles", params)
        if (result != null && result != "null") {
            try {
                styles = jsonToStyleMap(JSONObject(result))
            } catch (e: Exception) {
                android.util.Log.e("SpotCheckSDK", "[Wrapper] Failed to parse styles", e)
            }
        }
    }

    val wrapperSchema = ComponentStore.wrapper ?: return
    if (!ComponentStore.isLoaded) return

    val context = remember(state, styles) {
        BuilderContext(
            state = state,
            styles = styles,
            handlers = mapOf(
                "handleExitAnimationComplete" to { _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        executeBridge.execute("handleExitAnimationComplete")
                    }
                }
            ),
            slots = mapOf("children" to children),
        )
    }

    BuilderNode(schema = wrapperSchema, context = context)
}

private fun jsonToStyleMap(json: JSONObject): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = json.get(key)
        map[key] = when (value) {
            is JSONObject -> jsonToStyleMap(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}
