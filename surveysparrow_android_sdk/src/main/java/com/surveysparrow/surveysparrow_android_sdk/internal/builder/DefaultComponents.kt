package com.surveysparrow.surveysparrow_android_sdk.internal.builder

import android.graphics.Color.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.caverock.androidsvg.SVG
import android.graphics.drawable.PictureDrawable
import android.widget.ImageView
import org.json.JSONObject

/**
 * React Native `onLayout` shape expected by backend `handleSideTabLayout.js`
 * (`event.nativeEvent.layout.width` in density-independent pixels).
 */
internal fun rnLayoutEventFromSizePx(widthPx: Int, heightPx: Int, density: Density): JSONObject {
    val w = widthPx / density.density
    val h = heightPx / density.density
    val layout = JSONObject().apply {
        put("x", 0)
        put("y", 0)
        put("width", w.toDouble())
        put("height", h.toDouble())
    }
    return JSONObject().apply {
        put("nativeEvent", JSONObject().put("layout", layout))
    }
}

internal fun registerDefaultComponents() {
ComponentRegistry.register("Box") { props, children ->
    val density = LocalDensity.current
    val style = extractStyle(props)
    val onPress = props["onPress"] as? (Any?) -> Unit
    val onLayout = props["onLayout"] as? (Any?) -> Unit
    val animModifier = props["animationModifier"] as? Modifier ?: Modifier

    var lastLayoutW by remember { mutableIntStateOf(-1) }
    var lastLayoutH by remember { mutableIntStateOf(-1) }

    val flexDirection = style["flexDirection"] as? String
    val gap = (style["gap"] as? Number)?.toFloat() ?: 0f
    val contentAlignment = resolveAlignment(style["alignment"] as? String)

    var modifier = animModifier.then(applyStyle(style))

    if (onLayout != null) {
        modifier = modifier.onSizeChanged { size ->
            if (size.width == lastLayoutW && size.height == lastLayoutH) return@onSizeChanged
            lastLayoutW = size.width
            lastLayoutH = size.height
            onLayout.invoke(rnLayoutEventFromSizePx(size.width, size.height, density))
        }
    }

    var finalModifier = modifier

    if (onPress != null) {
        finalModifier = finalModifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            onPress.invoke(null)
        }
    }

    Box(
        modifier = finalModifier,
        contentAlignment = contentAlignment,
    ) {
        when (flexDirection) {
            "row" -> {
                Row(
                    horizontalArrangement = if (gap > 0f) {
                        Arrangement.spacedBy(gap.dp)
                    } else {
                        Arrangement.Start
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    children()
                }
            }

            "column" -> {
                Column(
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start,
                ) {
                    children()
                }
            }

            else -> {
                children()
            }
        }
    }
}

    ComponentRegistry.register("Column") { props, children ->
        val style = extractStyle(props)
        val animModifier = props["animationModifier"] as? Modifier ?: Modifier
        Column(
            modifier = animModifier.then(applyStyle(style)),
            verticalArrangement = when (style["alignment"]) {
                "topCenter" -> Arrangement.Top
                "bottomCenter" -> Arrangement.Bottom
                "center" -> Arrangement.Center
                else -> Arrangement.Top
            },
            horizontalAlignment = when (style["alignment"]) {
                "topCenter", "bottomCenter", "center" -> Alignment.CenterHorizontally
                "topEnd", "bottomEnd", "centerEnd" -> Alignment.End
                else -> Alignment.Start
            },
        ) {
            children()
        }
    }

    ComponentRegistry.register("Row") { props, children ->
        val style = extractStyle(props)
        val animModifier = props["animationModifier"] as? Modifier ?: Modifier
        Row(
            modifier = animModifier.then(applyStyle(style)),
            horizontalArrangement = when (style["horizontalArrangement"]) {
                "start" -> Arrangement.Start
                "end" -> Arrangement.End
                "center" -> Arrangement.Center
                "spaceBetween" -> Arrangement.SpaceBetween
                else -> Arrangement.Start
            },
            verticalAlignment = when (style["verticalAlignment"]) {
                "center" -> Alignment.CenterVertically
                "top" -> Alignment.Top
                "bottom" -> Alignment.Bottom
                else -> Alignment.CenterVertically
            },
        ) {
            children()
        }
    }

    ComponentRegistry.register("SafeArea") { props, children ->
        val style = extractStyle(props)
        val animModifier = props["animationModifier"] as? Modifier ?: Modifier
        val contentAlignment = resolveAlignment(style["alignment"] as? String)
        Box(
            modifier = animModifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .then(applyStyle(style)),
            contentAlignment = contentAlignment,
        ) {
            children()
        }
    }

    ComponentRegistry.register("Icon") { props, _ ->
        val style = extractStyle(props)
        val name = props["name"] as? String ?: "close"
        val size = (style["size"] as? Number)?.toInt() ?: 20
        val color = parseColorSafe(style["color"] as? String ?: "#000000")

        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = name,
            tint = color,
            modifier = Modifier.size(size.dp)
        )
    }

    ComponentRegistry.register("Text") { props, _ ->
        val style = extractStyle(props)
        val content = props["content"] as? String ?: ""
        val fontSize = (style["fontSize"] as? Number)?.toInt() ?: 14
        val color = parseColorSafe(style["color"] as? String ?: "#000000")
        val fontWeight = if (style["fontWeight"] == "bold") FontWeight.Bold else FontWeight.Normal

        Text(
            text = content,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
        )
    }

    ComponentRegistry.register("AsyncImage") { props, _ ->
        val style = extractStyle(props)
        val source = props["source"] as? Map<*, *>
        val uri = source?.get("uri") as? String ?: ""
        val w = (style["width"] as? Number)?.toInt() ?: 48
        val h = (style["height"] as? Number)?.toInt() ?: w
        val borderRadius = (style["borderRadius"] as? Number)?.toInt() ?: 0

        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(w.dp, h.dp)
                .clip(if (borderRadius > 100) CircleShape else RoundedCornerShape(borderRadius.dp))
        )
    }

    ComponentRegistry.register("SvgImage") { props, _ ->
        val xml = props["xml"] as? String ?: ""
        val w = (props["width"] as? Number)?.toInt() ?: 24
        val h = (props["height"] as? Number)?.toInt() ?: 24
        when {
            xml.isEmpty() -> { /* empty - render nothing */ }
            xml.startsWith("http") -> {
                AsyncImage(
                    model = xml,
                    contentDescription = null,
                    modifier = Modifier.size(w.dp, h.dp)
                )
            }
            else -> {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                            // Important: do not intercept touch events.
                            // The parent "Box" has the actual clickable `onPress` handler.
                            // If this ImageView consumes touches, only inner text becomes clickable.
                            isClickable = false
                            isFocusable = false
                            setOnTouchListener { _, _ -> false }
                            try {
                                val svg = SVG.getFromString(xml)
                                setImageDrawable(PictureDrawable(svg.renderToPicture()))
                            } catch (_: Exception) { /* ignore parse errors */ }
                        }
                    },
                    modifier = Modifier.size(w.dp, h.dp)
                )
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun extractStyle(props: Map<String, Any?>): Map<String, Any?> {
    return (props["style"] as? Map<String, Any?>) ?: emptyMap()
}

private fun resolveAlignment(alignment: String?): Alignment {
    return when (alignment) {
        "center" -> Alignment.Center
        "topCenter" -> Alignment.TopCenter
        "topStart" -> Alignment.TopStart
        "topEnd" -> Alignment.TopEnd
        "bottomCenter" -> Alignment.BottomCenter
        "bottomStart" -> Alignment.BottomStart
        "bottomEnd" -> Alignment.BottomEnd
        "centerStart" -> Alignment.CenterStart
        "centerEnd" -> Alignment.CenterEnd
        else -> Alignment.TopStart
    }
}

internal fun applyStyle(style: Map<String, Any?>): Modifier {
    var modifier = Modifier as Modifier

    val width = (style["width"] as? Number)?.toFloat()
    val height = (style["height"] as? Number)?.toFloat()
    val opacity = (style["opacity"] as? Number)?.toFloat()
    val bgColor = style["color"] as? String ?: style["backgroundColor"] as? String
    val borderRadius = (style["borderRadius"] as? Number)?.toFloat()
    val borderRadiusTopLeft = (style["borderRadiusTopLeft"] as? Number)?.toFloat()
    val borderRadiusTopRight = (style["borderRadiusTopRight"] as? Number)?.toFloat()
    val borderRadiusBottomLeft = (style["borderRadiusBottomLeft"] as? Number)?.toFloat()
    val borderRadiusBottomRight = (style["borderRadiusBottomRight"] as? Number)?.toFloat()
    val elevation = (style["elevation"] as? Number)?.toFloat()
    val padding = (style["padding"] as? Number)?.toFloat()
    val paddingVertical = (style["paddingVertical"] as? Number)?.toFloat()
    val paddingHorizontal = (style["paddingHorizontal"] as? Number)?.toFloat()
    val paddingBottom = (style["paddingBottom"] as? Number)?.toFloat()
    val paddingTop = (style["paddingTop"] as? Number)?.toFloat()
    val marginVertical = (style["marginVertical"] as? Number)?.toFloat()
    val marginHorizontal = (style["marginHorizontal"] as? Number)?.toFloat()
    val rotation = (style["rotation"] as? Number)?.toFloat()
    val translateX = (style["translateX"] as? Number)?.toFloat()
    val translateY = (style["translateY"] as? Number)?.toFloat()
    val flex = (style["flex"] as? Number)?.toInt()
    val gap = (style["gap"] as? Number)?.toFloat()
    val zIndexVal = (style["zIndex"] as? Number)?.toFloat()
    val positioned = style["positioned"] as? Boolean ?: false
    val centerVertical = style["centerVertical"] as? Boolean == true ||
        (style["centerVertical"] as? Number)?.toDouble() == 1.0
    val centerHorizontal = style["centerHorizontal"] as? Boolean == true ||
        (style["centerHorizontal"] as? Number)?.toDouble() == 1.0
    val alignment = style["alignment"] as? String
    val alignSelf = style["alignSelf"] as? String
    val top = (style["top"] as? Number)?.toFloat()
    val bottom = (style["bottom"] as? Number)?.toFloat()
    val left = (style["left"] as? Number)?.toFloat()
    val right = (style["right"] as? Number)?.toFloat()
    val clipBehavior = style["clipBehavior"] as? String
    val shadowShape = style["shadowShape"] as? String

    // Outer margin: must come before width/height. Padding after fixed size acted as inner inset and
    // made square close buttons (e.g. mini-card) non-square / not a true circle.
    if (marginVertical != null) modifier = modifier.padding(vertical = marginVertical.dp)
    if (marginHorizontal != null) modifier = modifier.padding(horizontal = marginHorizontal.dp)

    val squareSide = if (
        width != null && height != null &&
        width > 0f && height > 0f && width == height
    ) {
        width
    } else {
        null
    }

    if (squareSide != null) {
        modifier = modifier.size(squareSide.dp)
    } else {
        if (width != null && width > 0) modifier = modifier.width(width.dp)
        else if (width != null && width == 0f) modifier = modifier.width(0.dp)
        else if (width != null && width == -1f) modifier = modifier.fillMaxWidth()

        if (height != null && height > 0) modifier = modifier.height(height.dp)
        else if (height != null && height == 0f) modifier = modifier.height(0.dp)
        else if (height != null && height == -1f) modifier = modifier.fillMaxSize()
        else if (height != null && height == -2f) modifier = modifier.fillMaxHeight()
    }

    if (positioned && (centerVertical || centerHorizontal)) {
        when {
            centerVertical && centerHorizontal -> {
                var m = modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                if (top != null) m = m.padding(top = top.dp)
                if (bottom != null) m = m.padding(bottom = bottom.dp)
                if (left != null) m = m.padding(start = left.dp)
                if (right != null) m = m.padding(end = right.dp)
                modifier = m
            }
            centerVertical && !centerHorizontal -> {
                modifier = when {
                    left != null && right == null ->
                        modifier.fillMaxSize().wrapContentSize(Alignment.CenterStart).padding(start = left.dp)
                    right != null && left == null ->
                        modifier.fillMaxSize().wrapContentSize(Alignment.CenterEnd).padding(end = right.dp)
                    else ->
                        modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                }
            }
            !centerVertical && centerHorizontal -> {
                modifier = when {
                    top != null && bottom == null ->
                        modifier.fillMaxSize().wrapContentSize(Alignment.TopCenter).padding(top = top.dp)
                    bottom != null && top == null ->
                        modifier.fillMaxSize().wrapContentSize(Alignment.BottomCenter).padding(bottom = bottom.dp)
                    else ->
                        modifier.fillMaxSize().wrapContentSize(Alignment.TopCenter)
                }
            }
        }
    } else if (positioned && (top != null || bottom != null || left != null || right != null)) {
        when {
            right != null && top != null && left == null && bottom == null -> modifier = modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.TopEnd)
                .padding(top = top.dp, end = right.dp)
            left != null && top != null && right == null && bottom == null -> modifier = modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.TopStart)
                .padding(top = top.dp, start = left.dp)
            right != null && bottom != null && left == null && top == null -> modifier = modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.BottomEnd)
                .padding(bottom = bottom.dp, end = right.dp)
            left != null && bottom != null && right == null && top == null -> modifier = modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.BottomStart)
                .padding(bottom = bottom.dp, start = left.dp)
            else -> modifier = modifier.offset {
                IntOffset(
                    x = ((left ?: 0f) - (right ?: 0f)).dp.roundToPx(),
                    y = ((top ?: 0f) - (bottom ?: 0f)).dp.roundToPx(),
                )
            }
        }
    }

    // Transforms BEFORE shadow/background so the whole node (fill + children) matches RN/Expo.
    // Use separate graphicsLayers so compose order is explicit: translation (inner) then rotation (outer),
    // i.e. R·T·p — translate first in local space, then rotate the whole chip (side tab).
    // Opacity is outermost so it affects the fully transformed result.
    if (opacity != null) {
        modifier = modifier.graphicsLayer { this.alpha = opacity }
    }



    if (translateX != null || translateY != null) {
        modifier = modifier.graphicsLayer {
            val txPx = translateX?.let { tx -> tx.dp.toPx() } ?: 0f
            val tyPx = translateY?.let { ty -> ty.dp.toPx() } ?: 0f
            if (translateX != null) translationX = txPx
            if (translateY != null) translationY = tyPx
        }
    }

    if (rotation != null) {
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    }

    val shape = when {
        shadowShape == "circle" -> CircleShape
        else -> buildShape(borderRadius, borderRadiusTopLeft, borderRadiusTopRight, borderRadiusBottomLeft, borderRadiusBottomRight)
    }

    if (elevation != null && elevation > 0 && shape != null) {
        modifier = modifier.shadow(elevation.dp, shape)
    }

    // Clip before background so the fill is constrained to shape (circle, rounded rect, etc.).
    // Avoid background(color, shape) + clip(shape): redundant and can interact badly with elevation.
    when {
        bgColor != null && shape != null -> {
            modifier = modifier.clip(shape).background(parseColorSafe(bgColor))
        }
        bgColor != null -> {
            modifier = modifier.background(parseColorSafe(bgColor))
        }
        shape != null -> {
            modifier = modifier.clip(shape)
        }
    }

    if (padding != null) modifier = modifier.padding(padding.dp)
    if (paddingVertical != null || paddingHorizontal != null) {
        modifier = modifier.padding(
            horizontal = (paddingHorizontal ?: 0f).dp,
            vertical = (paddingVertical ?: 0f).dp
        )
    }
    if (paddingBottom != null) modifier = modifier.padding(bottom = paddingBottom.dp)
    if (paddingTop != null) modifier = modifier.padding(top = paddingTop.dp)

    if (zIndexVal != null) modifier = modifier.zIndex(zIndexVal)

    if (clipBehavior == "none") {
        modifier = modifier.graphicsLayer { clip = false }
    }

    return modifier
}

private fun buildShape(
    borderRadius: Float?,
    topLeft: Float?,
    topRight: Float?,
    bottomLeft: Float?,
    bottomRight: Float?,
): androidx.compose.ui.graphics.Shape? {
    if (topLeft != null || topRight != null || bottomLeft != null || bottomRight != null) {
        return RoundedCornerShape(
            topStart = (topLeft ?: 0f).dp,
            topEnd = (topRight ?: 0f).dp,
            bottomStart = (bottomLeft ?: 0f).dp,
            bottomEnd = (bottomRight ?: 0f).dp,
        )
    }
    if (borderRadius != null && borderRadius > 0) {
        return if (borderRadius > 100) CircleShape else RoundedCornerShape(borderRadius.dp)
    }
    return null
}

internal fun parseColorSafe(colorStr: String): Color {
    return try {
        if (colorStr.startsWith("rgba")) {
            val parts = colorStr.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
            Color(
                red = parts[0].toInt(),
                green = parts[1].toInt(),
                blue = parts[2].toInt(),
                alpha = (parts[3].toFloat() * 255).toInt()
            )
        } else {
            Color(parseColor(colorStr))
        }
    } catch (_: Exception) {
        Color.Transparent
    }
}
