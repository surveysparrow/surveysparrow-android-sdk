package com.surveysparrow.surveysparrow_android_sdk

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import android.content.res.Resources
import android.graphics.Color.parseColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SpotCheck(config: SpotCheckConfig) {
    var isButtonClicked by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    var colorValue = "#000000"
    if( (config.closeButtonStyle["ctaButton"])?.let { isHexColor(it) } == true) {
        colorValue = config.closeButtonStyle["ctaButton"] as String
    }
    val minHeight = minOf(config.currentQuestionHeight.dp, (config.maxHeight * LocalConfiguration.current.screenHeightDp).dp)
    val additionalHeight = if (config.isBannerImageOn) 200.dp else 0.dp
    val finalHeight = minHeight + additionalHeight

    if (isButtonClicked) {
        LaunchedEffect(true) {
            closeSpotCheck(config)
        }
    }

    if (config.isVisible) {
        Surface(
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxSize()
        ) {}

        Column(
            verticalArrangement = when (config.position) {
                "top" -> Arrangement.Top
                "center" -> Arrangement.Center
                "bottom" -> Arrangement.Bottom
                else -> Arrangement.Center
            },
        ) {
            Box(
                modifier = if (config.isFullScreenMode) {
                    Modifier.height(LocalConfiguration.current.screenHeightDp.dp)
                } else {
                    Modifier.height(finalHeight)
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.setSupportZoom(true)
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun onMessageReceive(message: String) {
                                        val gson = Gson()
                                        val spotCheckData: SpotCheckData =
                                            gson.fromJson(message, SpotCheckData::class.java)
                                        if (spotCheckData.type == "spotCheckData") {
                                            config.currentQuestionHeight =
                                                spotCheckData.data.currentQuestionSize.height
                                        }
                                        if (spotCheckData.type == "surveyCompleted") {
                                            config.onClose()
                                        }
                                    }
                                }, "Android")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                    }
                                }

                                loadUrl(config.spotCheckURL)
                            }
                        }
                    )
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    if (config.currentQuestionHeight != 0.0 && config.isCloseButtonEnabled) {
                        IconButton(
                            onClick = {
                                isButtonClicked = true
                                config.onClose()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(parseColor(colorValue)),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    else {
        Box(
            modifier = Modifier.background(Color.Transparent).height(0.dp).width(0.dp)
        )
    }
}

suspend fun trackScreen(screen: String, config: SpotCheckConfig) {
    val response = config.sendRequestForTrackScreen(screen)
    if(response) {
        val delayMillis = (config.afterDelay * 1000).toLong()
        Handler(Looper.getMainLooper()).postDelayed({
            config.openSpot()
            Log.i("TrackScreen", config.isVisible.toString())
        }, delayMillis)
    }else {
        Log.i("TrackScreen", "Failed")
    }
}

suspend fun trackEvent(screen: String, event: Map<String, Any>, config: SpotCheckConfig) {
    val response = config.sendEventTriggerRequest(screen, event)
    if(response) {
        val delayMillis = (config.afterDelay * 1000).toLong()
        Handler(Looper.getMainLooper()).postDelayed({
            config.openSpot()
        }, delayMillis)
    }
    Log.i("TrackEvent", config.isVisible.toString())
}
