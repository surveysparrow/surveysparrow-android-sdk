package com.surveysparrow.surveysparrow_android_sdk
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color.parseColor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


@Composable
fun SpotCheck(config: SpotCheckConfig) {



    var isCaptureImageActive by remember { mutableStateOf(false) }

    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 560
    var isButtonClicked by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }


    var colorValue = "#000000"
    if (config.closeButtonStyle["ctaButton"]?.let { isHexColor(it) } == true) {
        colorValue = config.closeButtonStyle["ctaButton"] as String
    }


    val minHeight = minOf(config.currentQuestionHeight.dp, (config.maxHeight * configuration.screenHeightDp).dp)
    val additionalHeight = if (config.isBannerImageOn) 90.dp else 0.dp
    val finalHeight = if (isTablet) minHeight else minHeight + additionalHeight

    var mUploadMessage: ValueCallback<Uri?>? by remember { mutableStateOf(null) }
    var mUploadMessageArray: ValueCallback<Array<Uri?>?>? by remember { mutableStateOf(null) }



    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (mUploadMessageArray == null && mUploadMessage == null) {
            return@rememberLauncherForActivityResult
        }

        val resultCode = result.resultCode
        val data = result.data

        if (resultCode == Activity.RESULT_OK) {
            mUploadMessageArray?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            mUploadMessageArray = null
        } else {
            mUploadMessageArray?.onReceiveValue(null)
            mUploadMessageArray = null
        }

    }



    val imageCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val extras: Bundle? = data?.extras
            val imageBitmap: Bitmap? = extras?.get("data") as? Bitmap
            val filename = "image_${System.currentTimeMillis()}.png"
            val file = File(context.filesDir, filename)
            capturedImageUri = Uri.fromFile(file)
            if (imageBitmap != null) {
                try {

                    FileOutputStream(file).use { out ->
                        imageBitmap?.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            out
                        )
                    }
                    Log.d("track", capturedImageUri.toString())
                    mUploadMessageArray?.onReceiveValue(arrayOf(capturedImageUri))
                    mUploadMessageArray = null
                    capturedImageUri = null
                } catch (e: IOException) {
                    Log.d("Photo Capture", "Error in photo Capture")
                    mUploadMessageArray = null
                    capturedImageUri = null

                }
            } else {
                mUploadMessageArray?.onReceiveValue(null)
                mUploadMessageArray = null
            }
        }
        isCaptureImageActive=false
    }


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

                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun captureImage() {
                                        if(!isCaptureImageActive){

                                        isCaptureImageActive = true  // Set flag when image capture starts
                                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                        if (intent.resolveActivity(context.packageManager) != null) {
                                            imageCaptureLauncher.launch(intent)
                                        } else {
                                            Log.d("Photo Capture", "No camera app found")
                                            isCaptureImageActive = false  // Reset flag if no camera app found
                                        }
                                    }
                                    }

                                }, "SsAndroidSdk")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri?>?>?,
                                        fileChooserParams: FileChooserParams?
                                    ): Boolean {

                                        if (mUploadMessageArray != null) {
                                            mUploadMessageArray?.onReceiveValue(null)
                                        }


                                        if (isCaptureImageActive) {
                                            mUploadMessageArray = filePathCallback
                                            return true
                                        }

                                        else{
                                            mUploadMessageArray = filePathCallback
                                            val intent = fileChooserParams?.createIntent()
                                            try {
                                                if (intent != null) {
                                                    fileChooserLauncher.launch(intent)
                                                }
                                            } catch (e: ActivityNotFoundException) {
                                                mUploadMessageArray = null
                                                Log.d("Upload-Questions", "Cannot open File chooser")
                                                return false
                                            }
                                        }
                                        return true
                                    }
                                }

                                loadUrl(config.spotCheckURL)
                            }
                        }
                    )
                    if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    if ((config.currentQuestionHeight != 0.0 || config.isFullScreenMode) && config.isCloseButtonEnabled) {
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
            modifier = Modifier
                .background(Color.Transparent)
                .height(0.dp)
                .width(0.dp)
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
            Log.i("TrackEvent", config.isVisible.toString())
        }, delayMillis)
    }else {
        Log.i("TrackScreen", "Failed")
    }
}