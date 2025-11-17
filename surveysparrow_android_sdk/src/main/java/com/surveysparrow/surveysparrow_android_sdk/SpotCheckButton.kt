package com.surveysparrow.surveysparrow_android_sdk

import android.graphics.Color
import android.util.Log
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

data class SpotCheckButtonConfig(
    val type: String = "floatingButton",
    val position: String = "bottom_right",
    val buttonSize: String = "medium",
    val backgroundColor: String = "#4A9CA6",
    val textColor: String = "#FFFFFF",
    val buttonText: String = "",
    val icon: String = "",
    val generatedIcon: String = "",
    val cornerRadius: String = "sharp",
    val onPress: () -> Unit = {}
)

object SpotCheckButtonUtils {

    fun hexToRgba(hex: String, opacity: Float): ComposeColor {
        if (!hex.matches(Regex("^#([A-Fa-f0-9]{3}){1,2}$"))) {
            return try {
                ComposeColor(Color.parseColor(hex)).copy(alpha = opacity)
            } catch (e: Exception) {
                ComposeColor.Black.copy(alpha = opacity)
            }
        }

        val cleanHex = hex.substring(1)
        val colorLong = when (cleanHex.length) {
            3 -> {
                val r = cleanHex[0].toString().repeat(2)
                val g = cleanHex[1].toString().repeat(2)
                val b = cleanHex[2].toString().repeat(2)
                "FF$r$g$b".toLong(16)
            }
            6 -> "FF$cleanHex".toLong(16)
            else -> 0xFF4A9CA6
        }
        return ComposeColor(colorLong).copy(alpha = opacity)
    }

    object SizeMaps {
        val FLOATING_BUTTON = mapOf(
            "small" to 28.dp,
            "medium" to 32.dp,
            "large" to 40.dp
        )

        val TEXT_BUTTON_ICON = mapOf(
            "small" to 16.dp,
            "medium" to 20.dp,
            "large" to 24.dp
        )

        val BORDER_RADIUS = mapOf(
            "sharp" to mapOf(
                "small" to 4.dp,
                "medium" to 6.dp,
                "large" to 8.dp
            ),
            "soft" to mapOf(
                "small" to 8.dp,
                "medium" to 12.dp,
                "large" to 16.dp
            ),
            "smooth" to mapOf(
                "small" to 24.dp,
                "medium" to 16.dp,
                "large" to 24.dp
            )
        )
    }

    fun getFloatingButtonSize(buttonSize: String): Dp {
        return SizeMaps.FLOATING_BUTTON[buttonSize] ?: SizeMaps.FLOATING_BUTTON["medium"]!!
    }

    fun getTextButtonIconSize(buttonSize: String): Dp {
        return SizeMaps.TEXT_BUTTON_ICON[buttonSize] ?: SizeMaps.TEXT_BUTTON_ICON["medium"]!!
    }

    fun getBorderRadius(cornerRadius: String, buttonSize: String): Dp {
        val radiusType = cornerRadius.ifEmpty { "sharp" }
        val size = buttonSize.ifEmpty { "medium" }
        return SizeMaps.BORDER_RADIUS[radiusType]?.get(size) ?: 6.dp
    }

    @Composable
    fun getTextStyle(buttonSize: String): TextStyle {
        return when (buttonSize) {
            "small" -> MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp,
                fontSize = 12.sp
            )
            "large" -> MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                fontSize = 16.sp
            )
            else -> MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun SpotCheckIcon(
    icon: String,
    buttonSize: String,
    type: String = "textButton",
    modifier: Modifier = Modifier
) {
    if (icon.isEmpty()) return

    val context = LocalContext.current
    val size = if (type == "floatingButton") {
        SpotCheckButtonUtils.getFloatingButtonSize(buttonSize)
    } else {
        SpotCheckButtonUtils.getTextButtonIconSize(buttonSize)
    }

    val isSvgString = icon.trimStart().startsWith("<svg", ignoreCase = true)
    val isUrl = icon.startsWith("http", ignoreCase = true)

    if (isSvgString) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply { setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null) }
            },
            modifier = modifier.size(size).clip(CircleShape),
            update = { imageView ->
                try {
                    val svg = com.caverock.androidsvg.SVG.getFromString(icon)
                    val drawable = android.graphics.drawable.PictureDrawable(svg.renderToPicture())
                    imageView.setImageDrawable(drawable)
                } catch (e: Exception) {
                    Log.e("SpotCheckIcon", "Failed to render SVG: ${e.message}")
                    e.printStackTrace()
                }
            }
        )
    } else if (isUrl) {
        val imageLoader = remember(context) {
            ImageLoader.Builder(context)
                .components { add(SvgDecoder.Factory()) }
                .build()
        }

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(icon)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "SpotCheck Icon",
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else {
        Log.w("SpotCheckIcon", "Unsupported icon format: $icon")
    }
}

@Composable
fun FloatingButton(config: SpotCheckButtonConfig) {
    val size = SpotCheckButtonUtils.getFloatingButtonSize(config.buttonSize)
    val innerBorderWidth = 4.dp
    val outerBorderWidth = 4.dp
    val bgColor = SpotCheckButtonUtils.hexToRgba(config.backgroundColor, 1f)
    val containerSize = size + innerBorderWidth * 2 + outerBorderWidth * 2

    val (verticalPos, horizontalPos) = remember(config.position) { config.position.split("_") }
    val verticalAlignment = when (verticalPos) {
        "top" -> Alignment.TopCenter
        "bottom" -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val horizontalAlignment = when (horizontalPos) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        contentAlignment = verticalAlignment
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            contentAlignment = when(horizontalAlignment) {
                Alignment.Start -> Alignment.CenterStart
                Alignment.End -> Alignment.CenterEnd
                else -> Alignment.Center
            }
        ) {
            Box(
                modifier = Modifier.size(containerSize)
                    .clip(CircleShape)
                    .background(SpotCheckButtonUtils.hexToRgba(config.backgroundColor, 0.25f))
                    .padding(outerBorderWidth)
                    .zIndex(1000000f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .background(SpotCheckButtonUtils.hexToRgba(config.backgroundColor, 0.5f))
                        .padding(innerBorderWidth),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(bgColor).clickable { config.onPress() },
                        contentAlignment = Alignment.Center
                    ) {
                        val svgSize = SpotCheckButtonUtils.getFloatingButtonSize(config.buttonSize) * 0.85f
                        SpotCheckIcon(
                            icon = config.generatedIcon.ifEmpty { config.icon },
                            buttonSize = config.buttonSize,
                            type = "floatingButton",
                            modifier = Modifier.size(svgSize)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SideTab(config: SpotCheckButtonConfig) {
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val textStyle = SpotCheckButtonUtils.getTextStyle(config.buttonSize)
    val borderRadius = SpotCheckButtonUtils.getBorderRadius(config.cornerRadius, config.buttonSize)
    val bgColor = SpotCheckButtonUtils.hexToRgba(config.backgroundColor, 1f)
    val textColor = SpotCheckButtonUtils.hexToRgba(config.textColor, 1f)

    val paddingValues = when (config.buttonSize) {
        "small" -> PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        "large" -> PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        else -> PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    }

    val (verticalPos, horizontalPos) = remember(config.position) { config.position.split("_") }

    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).zIndex(1000000f)
    ) {
        val alignment = when (verticalPos) {
            "top" -> when (horizontalPos) { "left" -> Alignment.TopStart; "right" -> Alignment.TopEnd; "center" -> Alignment.TopCenter; else -> Alignment.TopCenter }
            "bottom" -> when (horizontalPos) { "left" -> Alignment.BottomStart; "right" -> Alignment.BottomEnd; "center" -> Alignment.BottomCenter; else -> Alignment.BottomCenter }
            "center", "middle" -> when (horizontalPos) { "left" -> Alignment.CenterStart; "right" -> Alignment.CenterEnd; "center" -> Alignment.Center; else -> Alignment.Center }
            else -> Alignment.Center
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
            Box(
                modifier = Modifier.wrapContentSize()
                    .onSizeChanged { componentSize = it }
                    .graphicsLayer {
                        if (componentSize != IntSize.Zero && horizontalPos in listOf("left", "right")) {
                            val width = componentSize.width.toFloat()
                            val height = componentSize.height.toFloat()
                            rotationZ = if (horizontalPos == "left") 90f else -90f
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            translationX = if (horizontalPos == "left") -(width - height) / 2f else (width - height) / 2f
                            translationY = when (verticalPos) {
                                "top" -> (width) / 2f
                                "bottom" -> -(width) / 2f
                                else -> 0f
                            }
                        }
                    }
                    .clip(RoundedCornerShape(topStart = borderRadius, topEnd = borderRadius))
                    .background(bgColor)
                    .clickable { config.onPress() }
                    .padding(paddingValues)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val iconSize = SpotCheckButtonUtils.getTextButtonIconSize(config.buttonSize)
                    SpotCheckIcon(icon = config.generatedIcon.ifEmpty { config.icon }, buttonSize = config.buttonSize, type = "sideTab", modifier = Modifier.size(iconSize))
                    if (config.buttonText.isNotEmpty()) {
                        Text(text = config.buttonText, style = textStyle, color = textColor, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun TextButton(config: SpotCheckButtonConfig) {
    val textStyle = SpotCheckButtonUtils.getTextStyle(config.buttonSize)
    val borderRadius = SpotCheckButtonUtils.getBorderRadius(config.cornerRadius, config.buttonSize)
    val bgColor = SpotCheckButtonUtils.hexToRgba(config.backgroundColor, 1f)
    val textColor = SpotCheckButtonUtils.hexToRgba(config.textColor, 1f)

    val paddingValues = when (config.buttonSize) {
        "small" -> PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        "large" -> PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        else -> PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    }

    val (verticalPos, horizontalPos) = remember(config.position) { config.position.split("_") }
    val verticalAlignment = when (verticalPos) { "top" -> Alignment.Top; "bottom" -> Alignment.Bottom; else -> Alignment.CenterVertically }
    val horizontalAlignment = when (horizontalPos) { "left" -> Alignment.Start; "right" -> Alignment.End; else -> Alignment.CenterHorizontally }

    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).zIndex(1000000f),
        contentAlignment = when(verticalAlignment) {
            Alignment.Top -> when(horizontalAlignment){ Alignment.Start -> Alignment.TopStart; Alignment.End -> Alignment.TopEnd; else -> Alignment.TopCenter }
            Alignment.Bottom -> when(horizontalAlignment){ Alignment.Start -> Alignment.BottomStart; Alignment.End -> Alignment.BottomEnd; else -> Alignment.BottomCenter }
            else -> when(horizontalAlignment){ Alignment.Start -> Alignment.CenterStart; Alignment.End -> Alignment.CenterEnd; else -> Alignment.Center }
        }
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(borderRadius)).background(bgColor).clickable { config.onPress() }.padding(paddingValues),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpotCheckIcon(icon = config.generatedIcon.ifEmpty { config.icon }, buttonSize = config.buttonSize, type = "textButton")
            if (config.buttonText.isNotEmpty()) {
                Text(text = config.buttonText, style = textStyle, color = textColor, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun SpotCheckButton(config: SpotCheckButtonConfig, modifier: Modifier = Modifier) {
    when (config.type) {
        "floatingButton" -> FloatingButton(config)
        "sideTab" -> SideTab(config)
        "textButton" -> TextButton(config)
    }
}
