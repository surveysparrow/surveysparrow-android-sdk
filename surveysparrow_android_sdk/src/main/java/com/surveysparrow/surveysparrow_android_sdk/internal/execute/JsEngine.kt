package com.surveysparrow.surveysparrow_android_sdk.internal.execute

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class JsEngine(
    private val context: Context,
    private val domainName: String = "",
    private val sdkVersion: String = SDK_VERSION,
) {
    private var webView: WebView? = null
    private val bridge = NativeBridge()
    private var isReady = false
    private val readyDeferred = CompletableDeferred<Unit>()
    private val pendingCalls = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val callIdCounter = AtomicInteger(0)
    var onDispatch: ((String) -> Unit)? = null
    var onSaveData: ((String, String) -> Unit)? = null
    var onLoadData: ((Boolean, String) -> Unit)? = null
    var onSurveyLoaded: ((String) -> Unit)? = null
    var onSurveyResponse: ((String) -> Unit)? = null
    var onPartialSubmission: ((String) -> Unit)? = null
    var onCloseButtonTap: (() -> Unit)? = null
    var onCaptureError: ((String) -> Unit)? = null
    var onAddBreadcrumb: ((String, String) -> Unit)? = null
    var onPauseKeyboard: (() -> Unit)? = null
    var onResumeKeyboard: (() -> Unit)? = null
    var onWebViewInject: ((String, String) -> Unit)? = null
    var onExecuteError: ((String, String) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun initialize() {
        withContext(Dispatchers.Main) {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(bridge, "NativeBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(BOOTSTRAP_JS, null)
                        isReady = true
                        readyDeferred.complete(Unit)
                    }
                }
                loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "UTF-8", null)
            }
        }
    }

    suspend fun storeFunctions(functionsJson: String) {
        readyDeferred.await()
        withContext(Dispatchers.Main) {
            val js = "_storeFunctions($functionsJson);"
            webView?.evaluateJavascript(js, null)
        }
    }

    suspend fun execute(functionName: String, paramsJson: String, stateJson: String): String? {
        readyDeferred.await()

        val callId = "xc_${callIdCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<String?>()
        pendingCalls[callId] = deferred
        deferred.invokeOnCompletion { pendingCalls.remove(callId) }

        withContext(Dispatchers.Main) {
            // JSON is a valid JS literal, so params/state are passed as-is (no manual escaping).
            val js = "_execute(${JSONObject.quote(callId)},${JSONObject.quote(functionName)},$paramsJson,$stateJson);"
            webView?.evaluateJavascript(js, null)
        }

        val result = withTimeoutOrNull(EXECUTE_TIMEOUT_MS) { deferred.await() }
        if (result == null && pendingCalls.remove(callId) != null) {
            onExecuteError?.invoke(functionName, "Execution timed out")
        }
        return result
    }

    suspend fun evaluateExpr(expr: String, stateJson: String, stylesJson: String): String? {
        readyDeferred.await()
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<String?>()
            val escaped = expr.replace("\\", "\\\\").replace("'", "\\'")
            val escapedState = stateJson.replace("\\", "\\\\").replace("'", "\\'")
            val escapedStyles = stylesJson.replace("\\", "\\\\").replace("'", "\\'")
            webView?.evaluateJavascript(
                "_evalExpr('$escaped', '$escapedState', '$escapedStyles')"
            ) { result ->
                deferred.complete(result?.trim('"') ?: "null")
            }
            deferred.await()
        }
    }

    fun resolveCallback(callbackId: String, value: String) {
        Handler(Looper.getMainLooper()).post {
            webView?.evaluateJavascript("_resolveCallback(${JSONObject.quote(callbackId)}, ${JSONObject.quote(value)});", null)
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            webView?.destroy()
            webView = null
        }
    }

    inner class NativeBridge {

        @JavascriptInterface
        fun dispatch(updateJson: String) {
            onDispatch?.invoke(updateJson)
        }

        @JavascriptInterface
        fun saveData(data: String, callbackId: String) {
            onSaveData?.invoke(data, callbackId)
        }

        @JavascriptInterface
        fun loadData(isTraceId: Boolean, callbackId: String) {
            onLoadData?.invoke(isTraceId, callbackId)
        }

        @JavascriptInterface
        fun onSurveyLoadedBridge(responseJson: String) {
            onSurveyLoaded?.invoke(responseJson)
        }

        @JavascriptInterface
        fun onSurveyResponseBridge(responseJson: String) {
            onSurveyResponse?.invoke(responseJson)
        }

        @JavascriptInterface
        fun onPartialSubmissionBridge(responseJson: String) {
            onPartialSubmission?.invoke(responseJson)
        }

        @JavascriptInterface
        fun onCloseButtonTapBridge() {
            onCloseButtonTap?.invoke()
        }

        @JavascriptInterface
        fun captureError(errorJson: String) {
            onCaptureError?.invoke(errorJson)
        }

        @JavascriptInterface
        fun addBreadcrumb(message: String, dataJson: String) {
            onAddBreadcrumb?.invoke(message, dataJson)
        }

        @JavascriptInterface
        fun pauseKeyboard() {
            onPauseKeyboard?.invoke()
        }

        @JavascriptInterface
        fun resumeKeyboard() {
            onResumeKeyboard?.invoke()
        }

        @JavascriptInterface
        fun onExecuteResultBridge(callId: String, resultJson: String) {
            pendingCalls.remove(callId)?.complete(resultJson)
        }

        @JavascriptInterface
        fun onExecuteErrorBridge(callId: String, functionName: String, error: String, reported: Boolean) {
            pendingCalls.remove(callId)?.complete(null)
            // The function already reported this failure itself.
            if (reported) return
            onExecuteError?.invoke(functionName, error)
        }

        @JavascriptInterface
        fun webviewInject(target: String, script: String) {
            Handler(Looper.getMainLooper()).post { onWebViewInject?.invoke(target, script) }
        }

        @JavascriptInterface
        fun nativeFetch(url: String, method: String, headersJson: String, body: String, callbackId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = method
                    conn.connectTimeout = 30000
                    conn.readTimeout = 30000

                    try {
                        val headers = org.json.JSONObject(headersJson)
                        val keys = headers.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            conn.setRequestProperty(key, headers.getString(key))
                        }
                    } catch (_: Exception) {}

                    if (body.isNotEmpty() && method != "GET") {
                        conn.doOutput = true
                        OutputStreamWriter(conn.outputStream).use { it.write(body) }
                    }

                    val status = conn.responseCode
                    val stream = if (status in 200..399) conn.inputStream else conn.errorStream
                    val responseBody = if (stream != null) {
                        BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    } else ""
                    conn.disconnect()

                    val js = "_resolveFetch(${JSONObject.quote(callbackId)}, $status, ${JSONObject.quote(responseBody)});"
                    Handler(Looper.getMainLooper()).post {
                        webView?.evaluateJavascript(js, null)
                    }
                } catch (e: Exception) {
                    val js = "_rejectFetch(${JSONObject.quote(callbackId)}, ${JSONObject.quote(e.message ?: "Network error")});"
                    Handler(Looper.getMainLooper()).post {
                        webView?.evaluateJavascript(js, null)
                    }
                }
            }
        }
    }

    suspend fun evaluateJsInWebView(js: String): String? {
        readyDeferred.await()
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<String?>()
            webView?.evaluateJavascript(js) { result ->
                deferred.complete(result?.trim('"') ?: "null")
            }
            deferred.await()
        }
    }

    companion object {
        const val SDK_VERSION = "1.2.12-beta.1"
        private const val EXECUTE_TIMEOUT_MS = 15_000L

        private val BOOTSTRAP_JS = """
            // Chrome < 71 WebViews have no globalThis; backend functions may use it.
            if (typeof globalThis === 'undefined') { window.globalThis = window; }
            var _functions = {};
            var _callbacks = {};
            var _cbId = 0;
            var _fetchCallbacks = {};

            // Flags an error the function already reported, so execute skips its own report.
            function _markReported(error, reported) {
                if (error && typeof error === 'object') {
                    try { Object.defineProperty(error, '__ssReported', { value: true, configurable: true }); } catch(e) {}
                }
                if (error != null) reported.push(error);
            }

            function _wasReported(error, reported) {
                try {
                    if (error && typeof error === 'object' && error.__ssReported === true) return true;
                } catch(e) {}
                return error != null && reported.indexOf(error) !== -1;
            }

            // Keeps only primitive values, with strings capped.
            function _breadcrumbData(data) {
                var out = {};
                if (!data || typeof data !== 'object') return out;
                var keys = Object.keys(data).slice(0, 20);
                for (var i = 0; i < keys.length; i++) {
                    var v = data[keys[i]];
                    if (typeof v === 'string') out[keys[i]] = v.slice(0, 200);
                    else if (typeof v === 'number' || typeof v === 'boolean' || v === null) out[keys[i]] = v;
                }
                return out;
            }

            function _storeFunctions(data) {
                _functions = data;
            }

            function _resolveCallback(id, value) {
                if (_callbacks[id]) {
                    _callbacks[id](value);
                    delete _callbacks[id];
                }
            }

            function _resolveFetch(id, status, body) {
                if (_fetchCallbacks[id]) {
                    _fetchCallbacks[id].resolve({ status: status, body: body });
                    delete _fetchCallbacks[id];
                }
            }

            function _rejectFetch(id, error) {
                if (_fetchCallbacks[id]) {
                    _fetchCallbacks[id].reject(new TypeError(error));
                    delete _fetchCallbacks[id];
                }
            }

            window.fetch = function(url, options) {
                return new Promise(function(resolve, reject) {
                    var id = 'fetch_' + (++_cbId);
                    var method = (options && options.method) ? options.method : 'GET';
                    var headers = (options && options.headers) ? options.headers : {};
                    var body = (options && options.body) ? options.body : '';
                    _fetchCallbacks[id] = {
                        resolve: function(r) {
                            var responseBody = r.body;
                            var status = r.status;
                            resolve({
                                ok: status >= 200 && status < 300,
                                status: status,
                                statusText: '' + status,
                                headers: { get: function() { return null; } },
                                text: function() { return Promise.resolve(responseBody); },
                                json: function() {
                                    return new Promise(function(res, rej) {
                                        try { res(JSON.parse(responseBody)); }
                                        catch(e) { rej(e); }
                                    });
                                }
                            });
                        },
                        reject: reject
                    };
                    NativeBridge.nativeFetch(
                        '' + url,
                        method.toUpperCase(),
                        JSON.stringify(headers),
                        '' + (body || ''),
                        id
                    );
                });
            };

            function _execute(callId, functionName, params, state) {
                try {
                    var pathParts = functionName.split('.');
                    var funcString = _functions;
                    for (var i = 0; i < pathParts.length; i++) {
                        if (!funcString) break;
                        funcString = funcString[pathParts[i]];
                    }
                    if (!funcString || typeof funcString !== 'string') {
                        NativeBridge.onExecuteErrorBridge(callId, functionName, 'Function ' + functionName + ' not found', false);
                        return;
                    }
                    var func = eval('(' + funcString + ')');
                    params = params || {};
                    state = state || {};
                    var sdkVersion = '""" + SDK_VERSION + """';
                    params.sdkVersion = sdkVersion;
                    var reported = [];
                    var payload = {
                        params: params,
                        state: state,
                        dispatchWrapper: function(update) {
                            NativeBridge.dispatch(JSON.stringify(update));
                        },
                        storage: {
                            saveData: function(data) {
                                return new Promise(function(resolve) {
                                    var id = 'cb_' + (++_cbId);
                                    _callbacks[id] = resolve;
                                    NativeBridge.saveData(data || '', id);
                                });
                            },
                            loadData: function(isTraceId) {
                                return new Promise(function(resolve) {
                                    var id = 'cb_' + (++_cbId);
                                    _callbacks[id] = resolve;
                                    NativeBridge.loadData(!!isTraceId, id);
                                });
                            }
                        },
                        listener: {
                            onSurveyLoaded: function(response) {
                                NativeBridge.onSurveyLoadedBridge(JSON.stringify(response || {}));
                            },
                            onSurveyResponse: function(response) {
                                NativeBridge.onSurveyResponseBridge(JSON.stringify(response || {}));
                            },
                            onPartialSubmission: function(response) {
                                NativeBridge.onPartialSubmissionBridge(JSON.stringify(response || {}));
                            },
                            onCloseButtonTap: function() {
                                NativeBridge.onCloseButtonTapBridge();
                            }
                        },
                        sentry: {
                            captureP0Error: function(error, source, context) {
                                _markReported(error, reported);
                                NativeBridge.captureError(JSON.stringify({
                                    priority: 'P0',
                                    error: error ? (error.message || String(error)) : 'Unknown',
                                    source: source || 'GENERAL',
                                    context: context || {}
                                }));
                            },
                            captureP1Error: function(error, source, context) {
                                _markReported(error, reported);
                                NativeBridge.captureError(JSON.stringify({
                                    priority: 'P1',
                                    error: error ? (error.message || String(error)) : 'Unknown',
                                    source: source || 'GENERAL',
                                    context: context || {}
                                }));
                            },
                            addBreadcrumb: function(message, data) {
                                try {
                                    if (typeof message !== 'string' || !message) return;
                                    NativeBridge.addBreadcrumb(message.slice(0, 200), JSON.stringify(_breadcrumbData(data)));
                                } catch(e) {}
                            }
                        },
                        keyboard: {
                            pauseDefaultKeyboardBehavior: function() {
                                NativeBridge.pauseKeyboard();
                            },
                            resumeDefaultKeyboardBehavior: function() {
                                NativeBridge.resumeKeyboard();
                            }
                        },
                        webview: {
                            inject: function(target, script) {
                                NativeBridge.webviewInject(String(target), String(script));
                            }
                        }
                    };
                    var result = func(payload);
                    if (result && typeof result.then === 'function') {
                        result.then(function(r) {
                            NativeBridge.onExecuteResultBridge(callId, JSON.stringify(r === undefined ? null : r));
                        }).catch(function(e) {
                            NativeBridge.onExecuteErrorBridge(callId, functionName, functionName + ' rejected: ' + (e && e.message || e), _wasReported(e, reported));
                        });
                    } else {
                        NativeBridge.onExecuteResultBridge(callId, JSON.stringify(result === undefined ? null : result));
                    }
                } catch(e) {
                    NativeBridge.onExecuteErrorBridge(callId, functionName, functionName + ' threw: ' + (e && e.message || e), typeof reported !== 'undefined' && _wasReported(e, reported));
                }
            }

            function _evalExpr(expr, stateJson, stylesJson) {
                try {
                    var state = JSON.parse(stateJson);
                    var styles = JSON.parse(stylesJson);
                    var result = new Function('state', 'styles', 'Math', 'return ' + expr)(state, styles, Math);
                    return JSON.stringify(result);
                } catch(e) {
                    return 'null';
                }
            }
        """.trimIndent()
    }
}
