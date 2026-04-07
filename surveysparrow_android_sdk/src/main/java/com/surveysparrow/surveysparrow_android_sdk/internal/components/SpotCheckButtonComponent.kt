package com.surveysparrow.surveysparrow_android_sdk.internal.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.BuilderContext
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.BuilderNode
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.ComponentRegistry
import com.surveysparrow.surveysparrow_android_sdk.internal.execute.ExecuteBridge
import com.surveysparrow.surveysparrow_android_sdk.internal.state.ComponentStore
import com.surveysparrow.surveysparrow_android_sdk.internal.state.SpotCheckStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

internal fun registerSpotCheckButton(executeBridge: ExecuteBridge) {
    ComponentRegistry.register("SpotCheckButton") { _, _ ->
        SpotCheckButtonRoot(executeBridge = executeBridge)
    }
}

@Composable
internal fun SpotCheckButtonRoot(executeBridge: ExecuteBridge) {
    val state by SpotCheckStore.state.collectAsState()
    var styles by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    val spotCheckDetails = state.optJSONObject("SpotCheckState")
        ?.optJSONObject("spotCheckDetails")
    val isSpotCheckButton = spotCheckDetails?.optBoolean("isSpotCheckButton", false) ?: false
    val showSurveyContent = spotCheckDetails?.optBoolean("showSurveyContent", true) ?: true

    if (!isSpotCheckButton) return

    // Don't render button when survey is visible — they should never appear at same time
    if (showSurveyContent) return

    LaunchedEffect(state) {
        if (!isSpotCheckButton) return@LaunchedEffect
        val result = executeBridge.execute("spotCheckButton.getSpotCheckButtonStyles")
        if (result != null && result != "null") {
            try {
                styles = jsonToMap(JSONObject(result))
            } catch (_: Exception) {}
        }
    }

    val buttonSchema = ComponentStore.spotCheckButton ?: return
    if (styles.isEmpty()) return

    val context = remember(state, styles) {
        BuilderContext(
            state = state,
            styles = styles,
            handlers = mapOf(
                "handleSpotCheckButtonPress" to { _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        executeBridge.execute("spotCheckButton.handleSpotCheckButtonPress")
                    }
                },
                "handleSideTabLayout" to { arg ->
                    val event = arg as? JSONObject
                    if (event != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            executeBridge.execute(
                                "spotCheckButton.handleSideTabLayout",
                                JSONObject().put("event", event),
                            )
                        }
                    }
                },
                "handleExitAnimationComplete" to { _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        executeBridge.execute("handleExitAnimationComplete")
                    }
                },
            ),
        )
    }

    BuilderNode(schema = buttonSchema, context = context)
}

private fun jsonToMap(json: JSONObject): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = json.get(key)
        map[key] = when (value) {
            is JSONObject -> jsonToMap(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}
