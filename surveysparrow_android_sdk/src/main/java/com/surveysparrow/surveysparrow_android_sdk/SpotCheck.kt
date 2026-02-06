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
import androidx.activity.result.ActivityResult
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
import android.webkit.PermissionRequest
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import android.annotation.SuppressLint
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.DisposableEffect
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private object JsBridgeNames {
    const val ANDROID = "Android"
    const val FLUTTER = "flutterSpotCheckData"
    const val NATIVE = "SsAndroidSdk"
}

private object SpotCheckEvents {
    const val SLIDE_IN_FRAME = "slideInFrame"
    const val THANK_YOU_PAGE_SUBMISSION = "thankYouPageSubmission"
    const val SURVEY_COMPLETED = "surveyCompleted"
    const val SPOTCHECK_DATA = "spotCheckData"
    const val SURVEY_LOAD_STARTED = "surveyLoadStarted"
    const val PARTIAL_SUBMISSION = "partialSubmission"
    const val CLASSIC_LOADED = "classicLoadEvent"
    const val CHAT_LOADED = "chatLoadEvent"
}

private const val THANK_YOU_PAGE_DELAY = 4000L
private const val TABLET_SCREEN_WIDTH_DP = 560

private class SpotCheckState {
    var isCaptureImageActive by mutableStateOf(false)
    var capturedImageUri by mutableStateOf<Uri?>(null)
    var mUploadMessage: ValueCallback<Uri?>? by mutableStateOf(null)
    var mUploadMessageArray: ValueCallback<Array<Uri?>?>? by mutableStateOf(null)
    var audioPermissionGranted by mutableStateOf(false)
}

private class SpotCheckEventHandler(private val config: SpotCheckConfig) {
    private val gson = Gson()

    fun handleAndroidMessage(message: String) {
        val spotCheckData: SpotCheckData =
            gson.fromJson(message, SpotCheckData::class.java)
        if (spotCheckData.type == SpotCheckEvents.SPOTCHECK_DATA) {
            val currentSize =
                spotCheckData.data?.get("currentQuestionSize") as? Map<String, Any>
            val height =
                currentSize?.get("height") as Double
            config.currentQuestionHeight = height
        }
        if (spotCheckData.type == SpotCheckEvents.SURVEY_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                config.spotCheckListener?.onSurveyResponse(
                    spotCheckData.data
                )
            }
            config.onClose()
        }
    }

    fun handleFlutterSpotCheckData(message: String) {
        try {
            val jsonObject = JSONObject(message)
            if (!jsonObject.has("type") || jsonObject.isNull("type") || jsonObject.get("type") !is String) {
                return
            }
            if (!jsonObject.has("data") || jsonObject.isNull("data") || jsonObject.get("data") !is JSONObject) {
                return
            }
            val spotCheckData: SpotCheckData =
                gson.fromJson(message, SpotCheckData::class.java)

            if (spotCheckData.type == SpotCheckEvents.THANK_YOU_PAGE_SUBMISSION) {
                config.isThankyouPageSubmission = true
                CoroutineScope(Dispatchers.IO).launch {
                    config.spotCheckListener?.onSurveyResponse(
                        spotCheckData.data
                    )
                }

                if (config.spotChecksMode == "miniCard" && !config.isCloseButtonEnabled) {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(THANK_YOU_PAGE_DELAY)
                        config.onClose()
                    }
                }
                else{
                    config.isCloseButtonEnabled = true
                }
            }
            if (spotCheckData.type == SpotCheckEvents.SLIDE_IN_FRAME) {
                config.isMounted = true
            }
            if (spotCheckData.type == SpotCheckEvents.CLASSIC_LOADED){
                config.isClassicLoading = false
            }
            if (spotCheckData.type == SpotCheckEvents.CHAT_LOADED){
                config.isChatLoading = false
            }
        } catch (e: Exception) {
            Log.e("SpotCheck", e.message.toString())
        }
    }

    fun handleShareData(message: String) {
        val spotCheckData: Map<String, Any> = gson.fromJson(
            message,
            object : TypeToken<Map<String, Any>>() {}.type
        )
        val type = spotCheckData["type"] as? String
        if (type == SpotCheckEvents.SURVEY_LOAD_STARTED) {
            CoroutineScope(Dispatchers.IO).launch {
                config.spotCheckListener?.onSurveyLoaded(
                    spotCheckData
                )
            }
        }
    }

    fun handlePartialSubmission(message: String) {
        val spotCheckData: Map<String, Any> = gson.fromJson(
            message,
            object : TypeToken<Map<String, Any>>() {}.type
        )
        val type = spotCheckData["type"] as? String
        if (type == SpotCheckEvents.PARTIAL_SUBMISSION) {
            CoroutineScope(Dispatchers.IO).launch {
                config.spotCheckListener?.onPartialSubmission(
                    spotCheckData
                )
            }
        }
    }
}

@SuppressLint("JavascriptInterface")
private fun createAndroidJsInterface(eventHandler: SpotCheckEventHandler) = object : Any() {
    @JavascriptInterface
    fun onMessageReceive(message: String) {
        eventHandler.handleAndroidMessage(message)
    }
}

@SuppressLint("JavascriptInterface")
private fun createFlutterJsInterface(eventHandler: SpotCheckEventHandler) = object {
    @JavascriptInterface
    fun postMessage(message: String) {
        eventHandler.handleFlutterSpotCheckData(message)
    }
}

@SuppressLint("JavascriptInterface")
private fun createSsAndroidSdkJsInterface(
    eventHandler: SpotCheckEventHandler,
    launchCamera: () -> Unit
) = object : Any() {
    @JavascriptInterface
    fun captureImage() {
        launchCamera()
    }

    @JavascriptInterface
    fun shareData(message: String) {
        eventHandler.handleShareData(message)
    }

    @JavascriptInterface
    fun sendPartialSubmissionData(message: String) {
        eventHandler.handlePartialSubmission(message)
    }
}

private fun shouldShowClassic(config: SpotCheckConfig): Boolean {
    return (config.isMounted || config.isFullScreenMode) &&
            !config.isClassicLoading &&
            config.isVisible &&
            config.showSurveyContent &&
            config.isInjected &&
            config.spotCheckType == "classic"
}

private fun shouldShowChat(config: SpotCheckConfig): Boolean {
    return config.isFullScreenMode &&
            config.isVisible &&
            config.showSurveyContent &&
            !config.isChatLoading &&
            config.isInjected &&
            config.spotCheckType == "chat"
}

@Composable
private fun rememberLaunchers(
    state: SpotCheckState,
    onImageCaptured: (Uri?) -> Unit,
    onFileChosen: (Array<Uri?>?) -> Unit
): Triple<ManagedActivityResultLauncher<Intent, ActivityResult>, ManagedActivityResultLauncher<String, Boolean>, ManagedActivityResultLauncher<Intent, ActivityResult>> {
    val context = LocalContext.current

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (state.mUploadMessageArray == null && state.mUploadMessage == null) {
            return@rememberLauncherForActivityResult
        }

        val resultCode = result.resultCode
        val data = result.data

        if (resultCode == Activity.RESULT_OK) {
            onFileChosen(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
        } else {
            onFileChosen(null)
        }
    }

    val imageCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        var capturedImageUri: Uri? = null
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
                        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    onImageCaptured(capturedImageUri)
                } catch (e: IOException) {
                    Log.d("Photo Capture", "Error in photo Capture")
                    onImageCaptured(null)
                }
            } else {
                onImageCaptured(null)
            }
        } else {
            onImageCaptured(null)
        }
        state.isCaptureImageActive = false
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            try {
                if (isGranted) {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    imageCaptureLauncher.launch(intent)
                } else {
                    Log.d("Photo Capture", "Camera permission denied")
                    state.isCaptureImageActive = false
                }
            } catch (e: IOException) {
                Log.d("Photo Capture", "Error in photo Capture")
                state.isCaptureImageActive = false
            }
        }

    return Triple(fileChooserLauncher, permissionLauncher, imageCaptureLauncher)
}

@Composable
fun SpotCheck(config: SpotCheckConfig) {
    val state = remember { SpotCheckState() }
    val eventHandler = remember(config) { SpotCheckEventHandler(config) }
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > TABLET_SCREEN_WIDTH_DP

    var colorValue = "#000000"
    if (config.closeButtonStyle["ctaButton"]?.let { isHexColor(it) } == true) {
        colorValue = config.closeButtonStyle["ctaButton"] as String
    }

    var pendingPermissionRequest: PermissionRequest? = null
    val REQUEST_CODE_PERMISSIONS = 1234

    val minHeight =
        minOf(config.currentQuestionHeight.dp, (config.maxHeight * configuration.screenHeightDp).dp)
    val additionalHeight = if (config.isBannerImageOn) 90.dp else 0.dp
    val finalHeight = if (isTablet) minHeight else minHeight + additionalHeight

    val (fileChooserLauncher, permissionLauncher, imageCaptureLauncher) = rememberLaunchers(
        state = state,
        onImageCaptured = { uri ->
            state.mUploadMessageArray?.onReceiveValue(uri?.let { arrayOf(it) })
            state.mUploadMessageArray = null
            state.capturedImageUri = null
        },
        onFileChosen = { uris ->
            state.mUploadMessageArray?.onReceiveValue(uris)
            state.mUploadMessageArray = null
        }
    )

    fun launchCamera() {
        if (!state.isCaptureImageActive) {
            state.isCaptureImageActive = true
            try {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    imageCaptureLauncher.launch(intent)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            } catch (e: IOException) {
                Log.d("Photo Capture", "Error in photo Capture")
                state.isCaptureImageActive = false
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        state.audioPermissionGranted = isGranted
    }

    LaunchedEffect(Unit) {
        val isAlreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!isAlreadyGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            state.audioPermissionGranted = true
        }
    }

    fun extractActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    config.activity = remember(context) { extractActivity(context) }

    if (config.originalSoftInputMode == null) {
        config.originalSoftInputMode = config.activity?.window?.attributes?.softInputMode
    }

DisposableEffect(Unit) {
    onDispose {
    config.onClose(isNavigation = true)
    }
}

    if (config.classicUrl.isNotEmpty()) {
        val visibilityModifier = if (shouldShowClassic(config)) {
            Modifier
                .alpha(1f)
                .zIndex(1f)
        } else {
            Modifier
                .alpha(0f)
                .zIndex(-1f)
        }

        Box(
            modifier = visibilityModifier.background(Color.Black.copy(alpha = 0.3f))
        ) {}

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
                                    .clickable {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            config.closeSpotCheck()
                                            config.onClose()
                                        }
                                    }
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
                        SpotCheckWebView(
                            url = config.classicUrl,
                            config = config,
                            state = state,
                            eventHandler = eventHandler,
                            onPageFinished = {},
                            fileChooserLauncher = fileChooserLauncher,
                            launchCamera = ::launchCamera,
                            setWebViewRef = { config.classicWebViewRef = it }
                        )

                        if ((config.currentQuestionHeight != 0.0 || config.isFullScreenMode)
                            && config.isCloseButtonEnabled && config.spotChecksMode != "miniCard"
                        ) {
                            IconButton(
                                onClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        config.closeSpotCheck()
                                        config.onClose()
                                    }
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

        val visibilityModifier =
            if (shouldShowChat(config)) Modifier else Modifier.graphicsLayer { alpha = 0f }

        Box(
            modifier = visibilityModifier.background(Color.Black.copy(alpha = 0.3f))
        ) {}

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
                    SpotCheckWebView(
                        url = config.chatUrl,
                        config = config,
                        state = state,
                        eventHandler = eventHandler,
                        onPageFinished = {},
                        fileChooserLauncher = fileChooserLauncher,
                        launchCamera = ::launchCamera,
                        setWebViewRef = { config.chatWebViewRef = it },
                        isChat = true
                    )

                    if ((config.currentQuestionHeight != 0.0 || config.isFullScreenMode) && config.isCloseButtonEnabled) {
                        IconButton(
                            onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    config.closeSpotCheck()
                                    config.onClose()
                                }
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

    if (config.isSpotCheckButton && !config.showSurveyContent) {
        val buttonConfigMap = config.spotCheckButtonConfig
        if (buttonConfigMap.isNotEmpty()) {
            val buttonConfig = SpotCheckButtonConfig(
                type = buttonConfigMap["type"] as? String ?: "floatingButton",
                position = buttonConfigMap["position"] as? String ?: "bottom_right",
                buttonSize = buttonConfigMap["buttonSize"] as? String ?: "medium",
                backgroundColor = buttonConfigMap["backgroundColor"] as? String ?: "#4A9CA6",
                textColor = buttonConfigMap["textColor"] as? String ?: "#FFFFFF",
                buttonText = buttonConfigMap["buttonText"] as? String ?: "",
                icon = buttonConfigMap["icon"] as? String ?: "",
                generatedIcon = buttonConfigMap["generatedIcon"] as? String ?: "",
                cornerRadius = buttonConfigMap["cornerRadius"] as? String ?: "sharp",
                onPress = {
                    CoroutineScope(Dispatchers.IO).launch {
                        config.performSpotCheckApi()
                        config.showSurveyContent = true
                        config.openSpot()
                    }
                }
            )
            SpotCheckButton(config = buttonConfig)
        }
    }

}

@Composable
private fun SpotCheckWebView(
    url: String,
    config: SpotCheckConfig,
    state: SpotCheckState,
    eventHandler: SpotCheckEventHandler,
    onPageFinished: () -> Unit,
    fileChooserLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    launchCamera: () -> Unit,
    setWebViewRef: (WebView) -> Unit,
    isChat: Boolean = false
) {
    val context = LocalContext.current

    val webView = remember(url, isChat) {
        val existingWebView = if (isChat) config.chatWebViewRef else config.classicWebViewRef

        existingWebView ?: createWebView(
            context = context,
            url = url,
            isChat = isChat,
            state = state,
            eventHandler = eventHandler,
            onPageFinished = onPageFinished,
            fileChooserLauncher = fileChooserLauncher,
            launchCamera = launchCamera,
            setWebViewRef = setWebViewRef
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webView },
    )
}

private fun createWebView(
    context: Context,
    url: String,
    isChat: Boolean,
    state: SpotCheckState,
    eventHandler: SpotCheckEventHandler,
    onPageFinished: () -> Unit,
    fileChooserLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    launchCamera: () -> Unit,
    setWebViewRef: (WebView) -> Unit
): WebView {
    return WebView(context).apply {
        setWebViewRef(this)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        if (isChat) {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addJavascriptInterface(createAndroidJsInterface(eventHandler), JsBridgeNames.ANDROID)
        addJavascriptInterface(
            createFlutterJsInterface(eventHandler),
            JsBridgeNames.FLUTTER
        )
        addJavascriptInterface(
            createSsAndroidSdkJsInterface(eventHandler, launchCamera),
            JsBridgeNames.NATIVE
        )

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinished()
                view?.evaluateJavascript(
                    """
                        (function() {
                            const styleTag = document.createElement("style");
                            styleTag.innerHTML = `
                                   .surveysparrow-chat__wrapper .ss-language-selector--wrapper { 
                                        margin-right: 45px;                                             
                                   }                                      
                                   .close-btn-chat--spotchecks {
                                        display: none !important;
                                   }                                       
                            `;
                            document.body.appendChild(styleTag);
                        })();
                        """.trimIndent(),
                    null
                )
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri?>?>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (state.mUploadMessageArray != null) {
                    state.mUploadMessageArray?.onReceiveValue(null)
                }

                if (state.isCaptureImageActive) {
                    state.mUploadMessageArray = filePathCallback
                    return true
                }

                state.mUploadMessageArray = filePathCallback
                val intent = fileChooserParams?.createIntent()
                try {
                    if (intent != null) {
                        fileChooserLauncher.launch(intent)
                    }
                } catch (e: ActivityNotFoundException) {
                    state.mUploadMessageArray = null
                    Log.d("Upload-Questions", "Cannot open File chooser")
                    return false
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                (context as? Activity)?.runOnUiThread {
                    val requestedResources = request.resources
                    val permissions = requestedResources.mapNotNull {
                        when (it) {
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                            else -> null
                        }
                    }

                    val allGranted = permissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        request.grant(request.resources)
                    } else {
                        ActivityCompat.requestPermissions(
                            context,
                            permissions.toTypedArray(),
                            1234
                        )
                    }
                }
            }
        }
        loadUrl(url)
    }
}

suspend fun trackScreen(screen: String, config: SpotCheckConfig) {
    val response = config.sendRequestForTrackScreen(screen)
    if (response) {
        config.openSpot()
        Log.i("TrackScreen", config.isVisible.toString())
    } else {
        Log.i("TrackScreen", "Failed")
    }
}

suspend fun closeSpotchecks(config: SpotCheckConfig){
    config.closeSpotCheck()
    config.onClose(true)
}

suspend fun trackEvent(screen: String, event: Map<String, Any>, config: SpotCheckConfig) {
    val response = config.sendEventTriggerRequest(screen, event)
    if (response) {
        config.openSpot()
        Log.i("TrackEvent", config.isVisible.toString())
    } else {
        Log.i("TrackScreen", "Failed")
    }
}