package com.surveysparrow.surveysparrow_android_sdk.internal.builder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.json.JSONObject

internal data class BuilderContext(
    val state: JSONObject,
    val styles: Map<String, Any?>,
    /** Matches Expo: some helpers take params (e.g. `handleSideTabLayout` expects `event.nativeEvent.layout`). */
    val handlers: Map<String, (Any?) -> Unit> = emptyMap(),
    val slots: Map<String, @Composable () -> Unit> = emptyMap(),
)

@Composable
internal fun BuilderNode(
    schema: JSONObject,
    context: BuilderContext,
) {
    val resolver = remember(context) {
        BindingResolver(context.state, context.styles, context.handlers, context.slots)
    }

    when {
        schema.has("slot") -> {
            val slotName = schema.getString("slot")
            context.slots[slotName]?.invoke()
        }
        else -> {
            val ifCondition = schema.opt("if")
            val shouldRender = ifCondition == null || resolver.evaluateCondition(ifCondition)

            if (shouldRender) {
                val type = schema.optString("type", "")
                if (type.isNotEmpty()) {
                    val component = ComponentRegistry.get(type)
                    if (component != null) {
                        RenderComponent(schema, context, resolver, type, component)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderComponent(
    schema: JSONObject,
    context: BuilderContext,
    resolver: BindingResolver,
    type: String,
    component: ComposeComponentFactory,
) {
    val rawProps = schema.optJSONObject("props")
    val resolvedProps = mutableMapOf<String, Any?>()
    if (rawProps != null) {
        val keys = rawProps.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            resolvedProps[key] = resolver.resolve(rawProps.get(key))
        }
    }

    val contentValue = schema.opt("content")
    val resolvedContent = if (contentValue != null) resolver.resolve(contentValue)?.toString() else null

    val childrenArray = schema.optJSONArray("children")
    val childComposable: @Composable () -> Unit = if (resolvedContent != null) {
        { /* content is passed via props */ }
    } else {
        {
            if (childrenArray != null) {
                for (i in 0 until childrenArray.length()) {
                    childrenArray.optJSONObject(i)?.let { child ->
                        BuilderNode(schema = child, context = context)
                    }
                }
            }
        }
    }

    if (resolvedContent != null) {
        resolvedProps["content"] = resolvedContent
    }

    // Schema meta (e.g. webViewType chat/classic) reaches the component like on other platforms.
    schema.optJSONObject("meta")?.let { meta ->
        if (!resolvedProps.containsKey("meta")) resolvedProps["meta"] = jsonObjectToMap(meta)
    }

    component(resolvedProps, childComposable)
}

private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = when (val value = json.get(key)) {
            is JSONObject -> jsonObjectToMap(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}
