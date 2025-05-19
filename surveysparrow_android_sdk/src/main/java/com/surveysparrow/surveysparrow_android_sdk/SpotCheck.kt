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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage


@Composable
fun SpotCheck(config: SpotCheckConfig) {
    var isCaptureImageActive by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current


    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 560
    var isButtonClicked by remember { mutableStateOf(false) }


    var colorValue = "#000000"
    if (config.closeButtonStyle["ctaButton"]?.let { isHexColor(it) } == true) {
        colorValue = config.closeButtonStyle["ctaButton"] as String
    }


    val minHeight = minOf(config.currentQuestionHeight.dp, (config.maxHeight * configuration.screenHeightDp).dp)
    val additionalHeight = if (config.isBannerImageOn) 90.dp else 0.dp
    val finalHeight = if (isTablet) minHeight else minHeight + additionalHeight

    var mUploadMessage: ValueCallback<Uri?>? by remember { mutableStateOf(null) }
    var mUploadMessageArray: ValueCallback<Array<Uri?>?>? by remember { mutableStateOf(null) }


    fun extractActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    config.activity = remember(context) { extractActivity(context) }

    if(config.originalSoftInputMode==null) {
        config.originalSoftInputMode = config.activity?.window?.attributes?.softInputMode
    }

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


    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            try{
                if (isGranted) {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                imageCaptureLauncher.launch(intent)
            } else {
                Log.d("Photo Capture", "Camera permission denied")
                isCaptureImageActive = false
            }
        }
        catch (e:IOException){
            Log.d("Photo Capture", "Error in photo Capture")
            isCaptureImageActive = false
        }}


    if (isButtonClicked) {
        LaunchedEffect(true) {
            config.closeSpotCheck()
            config.onClose()
        }
    }

    if (config.classicUrl.isNotEmpty()) {

        val visibilityModifier = Modifier
            .then(
                if ((config.isMounted || config.isFullScreenMode) &&
                    !config.isClassicLoading &&
                    config.isVisible &&
                    config.spotCheckType == "classic"
                ) {
                    Modifier
                        .alpha(1f)
                        .zIndex(1f)
                } else {
                    Modifier
                        .alpha(0f)
                        .zIndex(-1f)
                }
            )

        Box(
            modifier = visibilityModifier.background(Color.Black.copy(alpha = 0.3f))
        ){}

        Column(
            modifier = visibilityModifier,
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
                    Modifier
                        .height(finalHeight)
                        .padding(if (config.spotChecksMode == "miniCard") 8.dp else 0.dp)


                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    if (config.spotChecksMode == "miniCard" && config.isCloseButtonEnabled == true) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { isButtonClicked=true }
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = CircleShape,
                                        ambientColor = Color.White.copy(alpha = 0.26f),
                                        spotColor = Color.White.copy(alpha = 0.26f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }


                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(
                                if (config.spotChecksMode == "miniCard") RoundedCornerShape(12.dp)
                                else RoundedCornerShape(0.dp)
                            )
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    config.classicWebViewRef = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.setSupportZoom(true)
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )


                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun postMessage(message: String) {
                                            val gson = Gson()
                                            val spotCheckData: SpotCheckData = gson.fromJson(message, SpotCheckData::class.java)

                                            if (spotCheckData.type == "spotCheckData") {
                                                val data = spotCheckData.data

                                                if(data.currentQuestionSize!=null){
                                                    config.currentQuestionHeight = spotCheckData.data.currentQuestionSize?.height ?: 0.0
                                                }
                                                else if(data.isCloseButtonEnabled == true){
                                                        config.isCloseButtonEnabled = spotCheckData.data.isCloseButtonEnabled
                                                            ?:false
                                                }
                                            }


                                            if (spotCheckData.type == "slideInFrame") {
                                                config.isMounted = true
                                            }
                                            if (spotCheckData.type == "surveyCompleted") {
                                                config.onClose()
                                            }
                                        }
                                    }, "flutterSpotCheckData")

                                    addJavascriptInterface(object : Any() {
                                        @JavascriptInterface
                                        fun captureImage() {
                                            if(!isCaptureImageActive){

                                                isCaptureImageActive = true

                                                try{

                                                    if(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){

                                                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)


                                                        imageCaptureLauncher.launch(intent)

                                                    }
                                                    else{
                                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                                    }

                                                }
                                                catch(e: IOException){
                                                    Log.d("Photo Capture", "Error in photo Capture")
                                                    isCaptureImageActive=false
                                                }
                                            }
                                        }

                                    }, "SsAndroidSdk")

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            config.isClassicLoading = false
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

                                    loadUrl(config.classicUrl)
                                }
                            }
                        )

                        if ((config.currentQuestionHeight != 0.0 || config.isFullScreenMode)
                            && config.isCloseButtonEnabled && config.spotChecksMode != "miniCard"
                        ) {
                            IconButton(
                                onClick = {
                                    isButtonClicked = true
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

                    if (config.avatarEnabled && config.spotChecksMode == "miniCard") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            ) {
                                AsyncImage(
                                    model = config.avatarUrl,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Green)
                                )
                            }
                        }
                    }
                }


            }
        }
    }


        if (config.chatUrl.isNotEmpty()) {

        val visibilityModifier = if ((config.isFullScreenMode) &&  config.isVisible && !config.isChatLoading && config.spotCheckType=="chat") Modifier else Modifier.graphicsLayer { alpha = 0f }

            Box(
            modifier = visibilityModifier.background(Color.Black.copy(alpha = 0.3f))
        ){}

        Column(
            modifier = visibilityModifier,
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
                                config.chatWebViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.setSupportZoom(true)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
                                                spotCheckData.data?.currentQuestionSize?.height!!
                                        }
                                        if (spotCheckData.type == "surveyCompleted") {
                                            config.onClose()
                                        }
                                    }
                                }, "Android")

                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun postMessage(message: String) {

                                        val gson = Gson()
                                        val spotCheckData: SpotCheckData =
                                            gson.fromJson(message, SpotCheckData::class.java)
                                        if (spotCheckData.type == "spotCheckData") {
                                            val data = spotCheckData.data

                                            if(data.currentQuestionSize!=null){
                                                config.currentQuestionHeight = spotCheckData.data.currentQuestionSize?.height ?: 0.0
                                            }
                                            else if(data.isCloseButtonEnabled == true){
                                                config.isCloseButtonEnabled = spotCheckData.data.isCloseButtonEnabled
                                                    ?:false
                                            }
                                        }
                                        if (spotCheckData.type == "slideInFrame") {
                                            config.isMounted = true
                                        }
                                        if (spotCheckData.type == "surveyCompleted") {
                                            config.onClose()
                                        }
                                    }
                                }, "flutterSpotCheckData")

                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun captureImage() {
                                        if (!isCaptureImageActive) {
                                            isCaptureImageActive = true
                                            try {
                                                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                                    imageCaptureLauncher.launch(intent)
                                                } else {
                                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            } catch (e: IOException) {
                                                Log.d("Photo Capture", "Error in photo Capture")
                                                isCaptureImageActive = false
                                            }
                                        }
                                    }
                                }, "SsAndroidSdk")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        config.isChatLoading = false
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
                                        } else {
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

                                loadUrl(config.chatUrl)
                            }
                        }
                    )

                    if ((config.currentQuestionHeight != 0.0 || config.isFullScreenMode) && config.isCloseButtonEnabled) {
                        IconButton(
                            onClick = {
                                isButtonClicked = true

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