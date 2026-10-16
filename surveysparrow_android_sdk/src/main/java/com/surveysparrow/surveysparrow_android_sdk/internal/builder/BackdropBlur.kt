package com.surveysparrow.surveysparrow_android_sdk.internal.builder

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Blurred snapshot of the app window behind the overlay (Android 8+; older devices get no blur).
private class BackdropBlurState {
    var image by mutableStateOf<ImageBitmap?>(null)
    var windowSize by mutableStateOf(IntSize.Zero)
    // Starts false so the overlay is hidden from its first frame until the snapshot is done.
    var ready by mutableStateOf(false)
    var positionInWindow = Offset.Zero
}

@Composable
internal fun Modifier.backdropBlur(blurDp: Float): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    val active = blurDp > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val state = remember(active, blurDp, configuration.screenWidthDp, configuration.screenHeightDp) { BackdropBlurState() }

    LaunchedEffect(state) {
        if (!active) return@LaunchedEffect
        try {
            // Overlay is hidden while capturing; wait until that frame is on screen.
            withFrameNanos { }
            withFrameNanos { }
            val window = view.context.findActivity()?.window ?: return@LaunchedEffect
            val decor = window.decorView
            if (decor.width <= 0 || decor.height <= 0) return@LaunchedEffect
            val sigmaPx = blurDp * density
            val scale = max(4, (sigmaPx / 12f).roundToInt())
            val small = captureWindow(window, decor.width / scale, decor.height / scale) ?: return@LaunchedEffect
            val blurred = withContext(Dispatchers.Default) { boxBlur(small, sigmaPx / scale) }
            state.windowSize = IntSize(decor.width, decor.height)
            state.image = blurred.asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            state.image = null
        } finally {
            state.ready = true
        }
    }

    if (!active) return this
    return this
        .graphicsLayer { alpha = if (state.ready) 1f else 0f }
        .onGloballyPositioned { state.positionInWindow = it.positionInWindow() }
        .drawBehind {
            val image = state.image ?: return@drawBehind
            val pos = state.positionInWindow
            drawImage(
                image = image,
                dstOffset = IntOffset(-pos.x.roundToInt(), -pos.y.roundToInt()),
                dstSize = state.windowSize,
                filterQuality = FilterQuality.Low,
            )
        }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@RequiresApi(Build.VERSION_CODES.O)
private suspend fun captureWindow(window: Window, width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(window, bitmap, { result ->
                if (cont.isActive) cont.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
            }, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }
}

// Three box-blur passes approximate a Gaussian with the given sigma.
private fun boxBlur(src: Bitmap, sigma: Float): Bitmap {
    val w = src.width
    val h = src.height
    val radius = ((sqrt(4f * sigma * sigma + 1f) - 1f) / 2f).roundToInt().coerceIn(1, 64)
    val a = IntArray(w * h)
    val b = IntArray(w * h)
    src.getPixels(a, 0, w, 0, 0, w, h)
    repeat(3) {
        blurPass(a, b, w, h, radius, horizontal = true)
        blurPass(b, a, w, h, radius, horizontal = false)
    }
    return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
}

private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val div = 2 * r + 1
    for (line in 0 until lines) {
        fun idx(i: Int): Int {
            val c = i.coerceIn(0, len - 1)
            return if (horizontal) line * w + c else c * w + line
        }
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val p = src[idx(i)]
            sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
        }
        for (i in 0 until len) {
            dst[idx(i)] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val pin = src[idx(i + r + 1)]
            val pout = src[idx(i - r)]
            sa += (pin ushr 24) - (pout ushr 24)
            sr += ((pin shr 16) and 0xFF) - ((pout shr 16) and 0xFF)
            sg += ((pin shr 8) and 0xFF) - ((pout shr 8) and 0xFF)
            sb += (pin and 0xFF) - (pout and 0xFF)
        }
    }
}
