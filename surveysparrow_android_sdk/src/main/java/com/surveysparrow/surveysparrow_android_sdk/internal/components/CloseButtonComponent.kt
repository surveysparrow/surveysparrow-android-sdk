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

internal fun registerCloseButton(executeBridge: ExecuteBridge) {
    ComponentRegistry.register("CloseButton") { _, _ ->
        CloseButtonContent(executeBridge = executeBridge)
    }
}

@Composable
private fun CloseButtonContent(executeBridge: ExecuteBridge) {
    val state by SpotCheckStore.state.collectAsState()
    var styles by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    val closeButton = state.optJSONObject("SpotCheckState")
        ?.optJSONObject("spotCheckDetails")
        ?.optJSONObject("closeButton")

    LaunchedEffect(closeButton?.toString()) {
        val result = executeBridge.execute("closeButton.getCloseButtonStyles")
        if (result != null && result != "null") {
            try {
                val stylesObj = JSONObject(result)
                styles = jsonToMap(stylesObj)
            } catch (_: Exception) {}
        }
    }

    val closeButtonSchema = ComponentStore.closeButton ?: return
    if (styles.isEmpty()) return

    val context = remember(state, styles) {
        BuilderContext(
            state = state,
            styles = styles,
            handlers = mapOf(
                "handleClosePress" to { _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        com.surveysparrow.surveysparrow_android_sdk.internal.SpotCheckSdk.instance.injectUnmountApp()
                        executeBridge.execute("closeButton.handleCloseButton")
                    }
                }
            ),
        )
    }

    BuilderNode(schema = closeButtonSchema, context = context)
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
