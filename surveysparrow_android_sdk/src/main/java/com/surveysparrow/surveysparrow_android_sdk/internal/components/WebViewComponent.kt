package com.surveysparrow.surveysparrow_android_sdk.internal.components

import com.surveysparrow.surveysparrow_android_sdk.internal.SpotCheckSdk
import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import androidx.activity.result.ActivityResultLauncher
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.surveysparrow.surveysparrow_android_sdk.LocalSpotCheckConfig
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.BuilderContext
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.BuilderNode
import com.surveysparrow.surveysparrow_android_sdk.internal.builder.ComponentRegistry
import com.surveysparrow.surveysparrow_android_sdk.internal.execute.ExecuteBridge
import com.surveysparrow.surveysparrow_android_sdk.internal.state.ComponentStore
import com.surveysparrow.surveysparrow_android_sdk.internal.state.SpotCheckStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

private val messageMutex = Mutex()

/** Stable per-[WebView] — survives [androidx.compose.runtime.key] / subtree recreation (see Builder). */
private const val SPOTCHECK_WEBVIEW_BRIDGE_TAG = 0x53504348
private const val SPOTCHECK_WEBVIEW_URI_TAG = 0x53504349

private class WebViewFileChooserBridge {
    var isCaptureImageActive: Boolean = false
    var uploadCallback: ValueCallback<Array<Uri?>?>? = null
    var fileChooser: ActivityResultLauncher<Intent>? = null
    var imageCapture: ActivityResultLauncher<Intent>? = null
    var cameraPermission: ActivityResultLauncher<String>? = null
}

private fun ensureWebViewFileChooserBridge(wv: WebView): WebViewFileChooserBridge {
    var b = wv.getTag(SPOTCHECK_WEBVIEW_BRIDGE_TAG) as? WebViewFileChooserBridge
    if (b == null) {
        b = WebViewFileChooserBridge()
        wv.setTag(SPOTCHECK_WEBVIEW_BRIDGE_TAG, b)
    }
    return b
}

internal object WebViewRefHolder {
    var classicWebView: WebView? = null
    var chatWebView: WebView? = null

    fun evaluateJsOnClassic(js: String) {
        classicWebView?.post { classicWebView?.evaluateJavascript(js, null) }
    }

    fun evaluateJsOnChat(js: String) {
        chatWebView?.post { chatWebView?.evaluateJavascript(js, null) }
    }
}

internal fun registerWebViewRenderer(executeBridge: ExecuteBridge) {
    ComponentRegistry.register("WebViewRenderer") { props, _ ->
        WebViewRendererContent(props = props, executeBridge = executeBridge)
    }
}

@Composable
internal fun WebViewContent(executeBridge: ExecuteBridge) {
    val state by SpotCheckStore.state.collectAsState()
    val webviewSchema = ComponentStore.webviewComponent ?: return

    val webViewDetails = state.optJSONObject("SpotCheckState")
        ?.optJSONObject("webViewDetails")
    val classicUrl = webViewDetails?.optString("classicUrl", "") ?: ""
    val chatUrl = webViewDetails?.optString("chatUrl", "") ?: ""
    val isClassicEnabled = webViewDetails?.optBoolean("isClassicEnabled", false) ?: false
    val isChatEnabled = webViewDetails?.optBoolean("isChatEnabled", false) ?: false
    val isChatLoading = webViewDetails?.optBoolean("isChatLoading", true) ?: true
    val isClassicLoading = webViewDetails?.optBoolean("isClassicLoading", true) ?: true
    val webViewInjectionData = webViewDetails?.opt("webViewInjectionData")?.let {
        if (it == JSONObject.NULL) "" else it.toString()
    } ?: ""
    val isCurrentSpotcheckChat = webViewDetails?.opt("isCurrentSpotcheckChat")?.let {
        if (it == JSONObject.NULL) null else it as? Boolean
    }

    // Backend injects via payload.webview.inject.
    LaunchedEffect(isChatLoading, isClassicLoading, webViewInjectionData) {
        executeBridge.execute("webviewComponent.handleWebViewInjection")
    }

    val context = remember(state) {
        BuilderContext(
            state = state,
            styles = emptyMap(),
            handlers = mapOf(
                "handleOnMessage" to { _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        executeBridge.execute("webviewComponent.handleWebViewMessage")
                    }
                },
                "handleOnError" to { _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        executeBridge.execute("webviewComponent.handleWebViewError")
                    }
                },
            ),
        )
    }

    BuilderNode(schema = webviewSchema, context = context)
}

@Composable
private fun WebViewRendererContent(
    props: Map<String, Any?>,
    executeBridge: ExecuteBridge,
) {
    val uri = props["uri"] as? String
    val canShow = props["canShow"] as? Boolean ?: false
    val meta = props["meta"] as? Map<*, *>
    val isChat = (meta?.get("webViewType") as? String) == "chat"

    if (uri.isNullOrEmpty()) {
        return
    }

    val config = LocalSpotCheckConfig.current
    val context = LocalContext.current

    val webView = remember(uri, isChat) {
        val configWebView = if (config != null) {
            if (isChat) config.chatWebViewRef else config.classicWebViewRef
        } else null
        // Apps often recreate the config on rotation; reuse the process-wide WebView for the same uri.
        val heldWebView = (if (isChat) WebViewRefHolder.chatWebView else WebViewRefHolder.classicWebView)
            ?.takeIf { it.getTag(SPOTCHECK_WEBVIEW_URI_TAG) == uri }
        val existingWebView = configWebView ?: heldWebView

        if (existingWebView != null) {
            // Rotation recreates the Activity; re-point the kept WebView at the current one.
            (existingWebView.context as? MutableContextWrapper)?.baseContext = context
            ensureWebViewFileChooserBridge(existingWebView)
            if (isChat) WebViewRefHolder.chatWebView = existingWebView
            else WebViewRefHolder.classicWebView = existingWebView
            if (config != null) {
                if (isChat) config.chatWebViewRef = existingWebView else config.classicWebViewRef = existingWebView
            }
            existingWebView
        } else {
            WebView(MutableContextWrapper(context)).apply {
                ensureWebViewFileChooserBridge(this)
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

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onMessageReceive(message: String) {
                        CoroutineScope(Dispatchers.IO).launch {
                            messageMutex.withLock {
                                executeBridge.execute("webviewComponent.handleWebViewMessage", buildWebViewMessageParams(message))
                            }
                        }
                    }
                }, "Android")

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postMessage(message: String) {
                        CoroutineScope(Dispatchers.IO).launch {
                            messageMutex.withLock {
                                executeBridge.execute("webviewComponent.handleWebViewMessage", buildWebViewMessageParams(message))
                            }
                        }
                    }
                }, "flutterSpotCheckData")

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun shareData(message: String) {
                        CoroutineScope(Dispatchers.IO).launch {
                            messageMutex.withLock {
                                executeBridge.execute("webviewComponent.handleWebViewMessage", buildWebViewMessageParams(message))
                            }
                        }
                    }

                    @JavascriptInterface
                    fun sendPartialSubmissionData(message: String) {
                        CoroutineScope(Dispatchers.IO).launch {
                            messageMutex.withLock {
                                executeBridge.execute("webviewComponent.handleWebViewMessage", buildWebViewMessageParams(message))
                            }
                        }
                    }

                    @JavascriptInterface
                    fun captureImage() {
                        post {
                            val b = ensureWebViewFileChooserBridge(this@apply)
                            if (b.isCaptureImageActive) return@post
                            b.isCaptureImageActive = true
                            try {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    b.imageCapture?.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                                        ?: run { b.isCaptureImageActive = false }
                                } else {
                                    b.cameraPermission?.launch(Manifest.permission.CAMERA)
                                        ?: run { b.isCaptureImageActive = false }
                                }
                            } catch (_: Exception) {
                                b.isCaptureImageActive = false
                            }
                        }
                    }
                }, "SsAndroidSdk")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(RN_WEBVIEW_POLYFILL_JS, null)
                        // Backend page script when sent; built-in one otherwise.
                        val remote = SpotCheckSdk.instance.remoteWebViewScript
                        if (remote != null) {
                            view?.evaluateJavascript(remote, null)
                        } else {
                            view?.evaluateJavascript(CSS_INJECTION_JS, null)
                            view?.evaluateJavascript(FOCUSIN_LISTENER_JS, null)
                        }

                        CoroutineScope(Dispatchers.IO).launch {
                            executeBridge.execute("webviewComponent.handleWebViewInjection")
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame != true) return
                        CoroutineScope(Dispatchers.IO).launch {
                            executeBridge.execute(
                                "webviewComponent.handleWebViewError",
                                JSONObject()
                                    .put("errorCode", error?.errorCode ?: -1)
                                    .put("description", error?.description?.toString() ?: ""),
                            )
                        }
                    }
                }

                if (config != null) {
                    if (isChat) {
                        config.chatWebViewRef = this
                        WebViewRefHolder.chatWebView = this
                    } else {
                        config.classicWebViewRef = this
                        WebViewRefHolder.classicWebView = this
                    }
                } else {
                    if (isChat) WebViewRefHolder.chatWebView = this
                    else WebViewRefHolder.classicWebView = this
                }
                setTag(SPOTCHECK_WEBVIEW_URI_TAG, uri)
                loadUrl(uri)
            }
        }
    }

    val imageCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val bridge = webView.getTag(SPOTCHECK_WEBVIEW_BRIDGE_TAG) as? WebViewFileChooserBridge
            ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            val filename = "image_${System.currentTimeMillis()}.png"
            val file = File(context.filesDir, filename)
            val outUri = Uri.fromFile(file)
            if (imageBitmap != null) {
                try {
                    FileOutputStream(file).use { out ->
                        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bridge.uploadCallback?.onReceiveValue(arrayOf(outUri))
                } catch (e: IOException) {
                    bridge.uploadCallback?.onReceiveValue(null)
                }
            } else {
                bridge.uploadCallback?.onReceiveValue(null)
            }
        } else {
            bridge.uploadCallback?.onReceiveValue(null)
        }
        bridge.uploadCallback = null
        bridge.isCaptureImageActive = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val bridge = webView.getTag(SPOTCHECK_WEBVIEW_BRIDGE_TAG) as? WebViewFileChooserBridge
            ?: return@rememberLauncherForActivityResult
        if (isGranted) {
            try {
                imageCaptureLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            } catch (_: Exception) {
                bridge.uploadCallback?.onReceiveValue(null)
                bridge.uploadCallback = null
                bridge.isCaptureImageActive = false
            }
        } else {
            bridge.uploadCallback?.onReceiveValue(null)
            bridge.uploadCallback = null
            bridge.isCaptureImageActive = false
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val bridge = webView.getTag(SPOTCHECK_WEBVIEW_BRIDGE_TAG) as? WebViewFileChooserBridge
            ?: return@rememberLauncherForActivityResult
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else null
        bridge.uploadCallback?.onReceiveValue(uris)
        bridge.uploadCallback = null
    }

    SideEffect {
        val b = ensureWebViewFileChooserBridge(webView)
        b.fileChooser = fileChooserLauncher
        b.imageCapture = imageCaptureLauncher
        b.cameraPermission = cameraPermissionLauncher
        webView.webChromeClient = SpotCheckWebChromeClient(webView, context)
    }

    AndroidView(
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
        update = {
            it.requestLayout()
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (canShow) 1f else 0f }
            .zIndex(if (canShow) 1f else -1f)
    )

}

private class SpotCheckWebChromeClient(
    private val webView: WebView,
    private val appContext: Context,
) : WebChromeClient() {

    override fun onShowFileChooser(
        view: WebView?,
        filePathCallback: ValueCallback<Array<Uri?>?>?,
        fileChooserParams: WebChromeClient.FileChooserParams?,
    ): Boolean {
        val wv = view ?: webView
        val b = ensureWebViewFileChooserBridge(wv)
        b.uploadCallback?.onReceiveValue(null)
        b.uploadCallback = filePathCallback
        if (b.isCaptureImageActive) {
            return true
        }
        val intent = fileChooserParams?.createIntent()
        return try {
            if (intent != null && b.fileChooser != null) {
                b.fileChooser!!.launch(intent)
                true
            } else {
                b.uploadCallback?.onReceiveValue(null)
                b.uploadCallback = null
                false
            }
        } catch (_: ActivityNotFoundException) {
            b.uploadCallback = null
            false
        }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        var ctx: Context? = appContext
        while (ctx is android.content.ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val activity = ctx as? Activity
        if (activity == null) {
            request.deny()
            return
        }
        activity.runOnUiThread {
            val allGranted = request.resources.all { res ->
                when (res) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    else -> true
                }
            }
            if (allGranted) request.grant(request.resources)
            else request.deny()
        }
    }
}

/**
 * Wraps a raw message string into the { event: { nativeEvent: { data: msg } } }
 * format that the backend handleWebViewMessage expects (RN onMessage shape).
 */
private fun buildWebViewMessageParams(message: String): JSONObject {
    return JSONObject().put(
        "event",
        JSONObject().put(
            "nativeEvent",
            JSONObject().put("data", message)
        )
    )
}

private const val RN_WEBVIEW_POLYFILL_JS = """(function(){if(!window.ReactNativeWebView){window.ReactNativeWebView={postMessage:function(d){try{SsAndroidSdk.shareData(d)}catch(e){try{Android.onMessageReceive(d)}catch(e2){}}}}}})();"""

private const val CSS_INJECTION_JS = """(function(){var s=document.createElement("style");s.innerHTML=".close-btn-chat--spotchecks{display:none!important;}";(document.head||document.documentElement).appendChild(s);})();"""

private const val FOCUSIN_LISTENER_JS = """(function(){document.addEventListener('focusin',function(e){var el=e.target;if(el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable)){var rect=el.getBoundingClientRect();try{SsAndroidSdk.shareData(JSON.stringify({type:'position',y:el.getBoundingClientRect().top}))}catch(err){}}});})();"""
