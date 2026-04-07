package com.surveysparrow.surveysparrow_android_sdk.internal.builder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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

    val animation = schema.optJSONObject("animation")

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

    if (animation != null) {
        val spotCheckState = context.state.optJSONObject("SpotCheckState")
        val currentSpotcheck = spotCheckState?.optJSONObject("currentSpotcheck")
        val spotCheckId = currentSpotcheck?.optInt("spotCheckId", 0) ?: 0
        val spotCheckContactId = currentSpotcheck?.optInt("spotCheckContactId", 0) ?: 0
        val spotCheckDetails = spotCheckState?.optJSONObject("spotCheckDetails")
        val isVisible = spotCheckDetails?.optBoolean("isVisible", false) ?: false
        val isExiting = spotCheckDetails?.optBoolean("isExiting", false) ?: false

        val animationKey = "${spotCheckId}_${spotCheckContactId}_${isVisible || isExiting}"
        logSpotcheckAnimation(
            "wrapped type=$type key=$animationKey visible=$isVisible exiting=$isExiting",
        )

        androidx.compose.runtime.key(animationKey) {
            AnimatedNode(
                animation = animation,
                resolver = resolver,
                context = context,
                content = { modifier ->
                    resolvedProps["animationModifier"] = modifier
                    component(resolvedProps, childComposable)
                },
            )
        }
    } else {
        component(resolvedProps, childComposable)
    }
}

/** Schema may omit `enter` (exit/layout only); null [enterConfig] uses rest pose and skips enter animation. */
@Composable
internal fun AnimatedNode(
    animation: JSONObject,
    resolver: BindingResolver,
    context: BuilderContext,
    content: @Composable (Modifier) -> Unit,
) {
    val enterConfig = animation.optJSONObject("enter")
    val exitConfig = animation.optJSONObject("exit")

    val enterTriggerRaw = enterConfig?.opt("trigger")
    val shouldEnter =
        enterConfig != null &&
            (enterTriggerRaw == null ||
                enterTriggerRaw === JSONObject.NULL ||
                resolver.evaluateCondition(enterTriggerRaw))

    val exitTriggerRaw = exitConfig?.opt("trigger")
    val shouldExit =
        exitConfig != null &&
            exitTriggerRaw != null &&
            exitTriggerRaw !== JSONObject.NULL &&
            resolver.evaluateCondition(exitTriggerRaw)

    logSpotcheckAnimation(
        "AnimatedNode shouldEnter=$shouldEnter shouldExit=$shouldExit hasEnter=${enterConfig != null} hasExit=${exitConfig != null}",
    )

    val density = LocalDensity.current

    val initialOpacity = remember(enterConfig) {
        if (enterConfig != null) {
            val trigger = enterConfig.opt("trigger")
            val triggerValue = when {
                trigger == null || trigger === JSONObject.NULL -> true
                else -> resolver.evaluateCondition(trigger)
            }
            if (triggerValue) {
                val from = enterConfig.optJSONObject("from") ?: JSONObject()
                resolveAnimValue(from.opt("opacity"), resolver)?.toFloat() ?: 0f
            } else {
                val to = enterConfig.optJSONObject("to") ?: JSONObject()
                resolveAnimValue(to.opt("opacity"), resolver)?.toFloat() ?: 1f
            }
        } else {
            1f
        }
    }

    val initialTranslateY = remember(enterConfig) {
        if (enterConfig != null) {
            val trigger = enterConfig.opt("trigger")
            val triggerValue = when {
                trigger == null || trigger === JSONObject.NULL -> true
                else -> resolver.evaluateCondition(trigger)
            }
            if (triggerValue) {
                val from = enterConfig.optJSONObject("from") ?: JSONObject()
                resolveAnimValue(from.opt("translateY"), resolver)?.toFloat() ?: 0f
            } else {
                val to = enterConfig.optJSONObject("to") ?: JSONObject()
                resolveAnimValue(to.opt("translateY"), resolver)?.toFloat() ?: 0f
            }
        } else {
            0f
        }
    }

    val initialTranslateX = remember(enterConfig) {
        if (enterConfig != null) {
            val trigger = enterConfig.opt("trigger")
            val triggerValue = when {
                trigger == null || trigger === JSONObject.NULL -> true
                else -> resolver.evaluateCondition(trigger)
            }
            if (triggerValue) {
                val from = enterConfig.optJSONObject("from") ?: JSONObject()
                resolveAnimValue(from.opt("translateX"), resolver)?.toFloat() ?: 0f
            } else {
                val to = enterConfig.optJSONObject("to") ?: JSONObject()
                resolveAnimValue(to.opt("translateX"), resolver)?.toFloat() ?: 0f
            }
        } else {
            0f
        }
    }

    val initialScale = remember(enterConfig) {
        if (enterConfig != null) {
            val trigger = enterConfig.opt("trigger")
            val triggerValue = when {
                trigger == null || trigger === JSONObject.NULL -> true
                else -> resolver.evaluateCondition(trigger)
            }
            if (triggerValue) {
                val from = enterConfig.optJSONObject("from") ?: JSONObject()
                resolveAnimValue(from.opt("scale"), resolver)?.toFloat() ?: 1f
            } else {
                val to = enterConfig.optJSONObject("to") ?: JSONObject()
                resolveAnimValue(to.opt("scale"), resolver)?.toFloat() ?: 1f
            }
        } else {
            1f
        }
    }

    val opacity = remember { Animatable(initialOpacity) }
    val translateY = remember { Animatable(initialTranslateY) }
    val translateX = remember { Animatable(initialTranslateX) }
    val scale = remember { Animatable(initialScale) }

    val prevEnterTrigger = remember { mutableStateOf(false) }
    val prevExitTrigger = remember { mutableStateOf(false) }

    LaunchedEffect(shouldEnter) {
        if (!shouldEnter && enterConfig != null && !prevEnterTrigger.value) {
            val to = enterConfig.optJSONObject("to") ?: JSONObject()
            val toOpacity = resolveAnimValue(to.opt("opacity"), resolver)?.toFloat() ?: 1f
            val toTY = resolveAnimValue(to.opt("translateY"), resolver)?.toFloat() ?: 0f
            val toTX = resolveAnimValue(to.opt("translateX"), resolver)?.toFloat() ?: 0f
            val toScale = resolveAnimValue(to.opt("scale"), resolver)?.toFloat() ?: 1f
            opacity.snapTo(toOpacity)
            translateY.snapTo(toTY)
            translateX.snapTo(toTX)
            scale.snapTo(toScale)
            logSpotcheckAnimation("enter skipped: snap to rest opacity=$toOpacity ty=$toTY scale=$toScale")
        }
    }

    LaunchedEffect(shouldEnter) {
        if (shouldEnter && !prevEnterTrigger.value && enterConfig != null) {
            val from = enterConfig.optJSONObject("from") ?: JSONObject()
            val to = enterConfig.optJSONObject("to") ?: JSONObject()
            val duration = enterConfig.optInt("duration", 300)
            val easingName = enterConfig.optString("easing", "easeOut")

            val fromOpacity = resolveAnimValue(from.opt("opacity"), resolver)?.toFloat() ?: 0f
            val toOpacity = resolveAnimValue(to.opt("opacity"), resolver)?.toFloat() ?: 1f
            val fromTY = resolveAnimValue(from.opt("translateY"), resolver)?.toFloat() ?: 0f
            val toTY = resolveAnimValue(to.opt("translateY"), resolver)?.toFloat() ?: 0f
            val fromTX = resolveAnimValue(from.opt("translateX"), resolver)?.toFloat() ?: 0f
            val toTX = resolveAnimValue(to.opt("translateX"), resolver)?.toFloat() ?: 0f
            val fromScale = resolveAnimValue(from.opt("scale"), resolver)?.toFloat() ?: 1f
            val toScale = resolveAnimValue(to.opt("scale"), resolver)?.toFloat() ?: 1f

            logSpotcheckAnimation(
                "enter start dur=$duration easing=$easingName " +
                    "from(op=$fromOpacity ty=$fromTY tx=$fromTX sc=$fromScale) " +
                    "to(op=$toOpacity ty=$toTY tx=$toTX sc=$toScale)",
            )

            opacity.snapTo(fromOpacity)
            translateY.snapTo(fromTY)
            translateX.snapTo(fromTX)
            scale.snapTo(fromScale)

            val easing = getEasing(easingName)
            launch { opacity.animateTo(toOpacity, tween(duration, easing = easing)) }
            launch { translateY.animateTo(toTY, tween(duration, easing = easing)) }
            launch { translateX.animateTo(toTX, tween(duration, easing = easing)) }
            launch { scale.animateTo(toScale, tween(duration, easing = easing)) }
        }
        prevEnterTrigger.value = shouldEnter
    }

    LaunchedEffect(shouldExit) {
        if (shouldExit && !prevExitTrigger.value && exitConfig != null) {
            val to = exitConfig.optJSONObject("to") ?: JSONObject()
            val duration = exitConfig.optInt("duration", 300)
            val easingName = exitConfig.optString("easing", "easeIn")

            val toOpacity = resolveAnimValue(to.opt("opacity"), resolver)?.toFloat() ?: 0f
            val toTY = resolveAnimValue(to.opt("translateY"), resolver)?.toFloat() ?: 0f
            val toTX = resolveAnimValue(to.opt("translateX"), resolver)?.toFloat() ?: 0f
            val toScale = resolveAnimValue(to.opt("scale"), resolver)?.toFloat() ?: 1f

            logSpotcheckAnimation(
                "exit start dur=$duration easing=$easingName to(op=$toOpacity ty=$toTY tx=$toTX sc=$toScale)",
            )

            val easing = getEasing(easingName)
            val opacityJob =
                if (toOpacity < 0.999f) {
                    launch { opacity.animateTo(toOpacity, tween(duration, easing = easing)) }
                } else {
                    null
                }
            val tyJob = launch { translateY.animateTo(toTY, tween(duration, easing = easing)) }
            val txJob = launch { translateX.animateTo(toTX, tween(duration, easing = easing)) }
            val scaleJob = launch { scale.animateTo(toScale, tween(duration, easing = easing)) }

            opacityJob?.join()
            tyJob.join()
            txJob.join()
            scaleJob.join()

            val onComplete = exitConfig.optString("onComplete", "")
            if (onComplete.isNotEmpty()) {
                logSpotcheckAnimation("exit complete -> handler=$onComplete")
                context.handlers[onComplete]?.invoke(null)
            }
        }
        prevExitTrigger.value = shouldExit
    }

    val transformOrigin = remember(enterConfig, exitConfig, shouldEnter, shouldExit) {
        val config = if (shouldExit) exitConfig else enterConfig
        if (config != null && config.has("transformOrigin")) {
            val originValue = resolver.resolve(config.get("transformOrigin"))
            parseTransformOrigin(originValue?.toString())
        } else {
            androidx.compose.ui.graphics.TransformOrigin.Center
        }
    }

    val alphaSnapshot = opacity.value
    val scaleSnapshot = scale.value
    val txPx = with(density) { translateX.value.dp.toPx() }
    val tyPx = with(density) { translateY.value.dp.toPx() }

    val modifier = Modifier.graphicsLayer {
        alpha = alphaSnapshot
        translationX = txPx
        translationY = tyPx
        scaleX = scaleSnapshot
        scaleY = scaleSnapshot
        this.transformOrigin = transformOrigin
    }

    content(modifier)
}

private fun parseTransformOrigin(value: String?): androidx.compose.ui.graphics.TransformOrigin {
    if (value == null) return androidx.compose.ui.graphics.TransformOrigin.Center

    val parts = value.trim().split(Regex("\\s+"))
    if (parts.size != 2) return androidx.compose.ui.graphics.TransformOrigin.Center

    val x = parts[0].removeSuffix("%").toFloatOrNull()?.div(100f) ?: 0.5f
    val y = parts[1].removeSuffix("%").toFloatOrNull()?.div(100f) ?: 0.5f

    return androidx.compose.ui.graphics.TransformOrigin(x, y)
}

private fun resolveAnimValue(value: Any?, resolver: BindingResolver): Double? {
    if (value == null || value == JSONObject.NULL) return null
    if (value is Number) return value.toDouble()
    if (value is JSONObject && value.has("\$expr")) {
        val result = resolver.resolveExpr(value.getString("\$expr"))
        return when (result) {
            is Number -> result.toDouble()
            is String -> result.toDoubleOrNull()
            else -> null
        }
    }
    return null
}

private fun getEasing(name: String) = when (name) {
    "linear" -> LinearEasing
    "easeIn" -> FastOutLinearInEasing
    "easeOut" -> LinearOutSlowInEasing
    "easeInOut", "easeInEaseOut" -> FastOutSlowInEasing
    else -> FastOutSlowInEasing
}
