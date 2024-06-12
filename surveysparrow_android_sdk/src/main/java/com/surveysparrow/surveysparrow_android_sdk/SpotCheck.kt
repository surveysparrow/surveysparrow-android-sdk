package com.surveysparrow.surveysparrow_android_sdk

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
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
    var isLoading by remember { mutableStateOf(true) }

    val minHeight = minOf(config.currentQuestionHeight.dp, (config.maxHeight * LocalConfiguration.current.screenHeightDp).dp)
    val additionalHeight = if (config.isBannerImageOn) 200.dp else 0.dp
    val finalHeight = minHeight + additionalHeight

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
                                        if (spotCheckData.type == "closeModal") {
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


class SpotCheckConfig(
    private var email: String,
    var domainName: String,
    var targetToken: String,
    private var firstName: String = "",
    private var lastName: String = "",
    private var phoneNumber: String = "",
    private var variables: Map<String, Any> = mapOf(),
    private var customProperties: Map<String, Any> = mapOf(),
) {
    var position by mutableStateOf("")
    var spotCheckURL by mutableStateOf("")
    private var triggerToken by mutableStateOf("")

    private var spotCheckID by mutableDoubleStateOf(0.0)
    private var spotCheckContactID by mutableDoubleStateOf(0.0)
    private var selectedSpotCheckID by mutableIntStateOf(0)

    var maxHeight by mutableDoubleStateOf(0.5)
    var afterDelay by mutableDoubleStateOf(0.0)
    var currentQuestionHeight by mutableDoubleStateOf(0.0)

    var isVisible by mutableStateOf(false)
    var isFullScreenMode by mutableStateOf(false)
    var isBannerImageOn by mutableStateOf(false)

    private var customEventsSpotChecks by mutableStateOf<List<Map<String, Any?>>>(listOf(mapOf()))
    private var traceId: String = ""

    init {
        if ( traceId.isEmpty() ) {
            traceId = generateTraceId()
        }
    }

    suspend fun sendRequestForTrackScreen(screenName: String): Boolean {
        var isSpotPassed by mutableStateOf(false)
        var isChecksPassed by mutableStateOf(false)

        try {
            val payload = PropertiesRequestPayload(
                screenName = screenName,
                variables = variables,
                customProperties = customProperties,
                userDetails = UserDetails(
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = phoneNumber
                ),
                visitor = Visitor(
                    deviceType = "Mobile",
                    operatingSystem = "Android",
                    screenResolution = getScreenResolution(),
                    currentDate = getCurrentDate(),
                    timezone = TimeZone.getDefault().id
                ),
                traceId = traceId
            )
            customEventsSpotChecks = listOf(mapOf())
            val apiService = RetrofitClient.create("https://${domainName}")
            val response = apiService.fetchProperties(targetToken, payload)
            Log.i("sendRequestForTrackScreen", response.toString())

            if (response.show != null) {

                val show: Boolean = response.show

                if (show) {
                    this.triggerToken = response.triggerToken
                    response.appearance?.let { setAppearance(it) }
                    isSpotPassed = true
                    response.spotCheckId?.also { spotCheckID = it.toDouble() }
                    response.spotCheckContactId?.also { spotCheckContactID = it.toDouble() }
                    spotCheckURL =
                        "https://$domainName/n/spotcheck/$triggerToken?spotcheckContactId=${String.format("%.0f", spotCheckContactID)}&traceId=$traceId&spotcheckUrl=$screenName"
                    return true
                } else {
                    Log.d(
                        "SPOT-CHECK",
                        "Error: Spots or Checks or Visitor or Recurrence Condition Failed"
                    )
                    return false
                }
            } else {
                Log.d(
                    "SPOT-CHECK",
                    "Show Not Received"
                )
            }


            if (response.checkPassed != null) {

                if (!isSpotPassed) {
                    val checkPassed: Boolean = response.checkPassed
                    if (checkPassed) {
                        val checkCondition = response.checkCondition
                        checkCondition?.let { condition ->
                            val afterDelay = condition["afterDelay"] as? String
                            afterDelay?.let { delay ->
                                val afterDelayDouble = delay.toDoubleOrNull() ?: 0.0
                                this.afterDelay = afterDelayDouble
                            }
                            val customEvent = condition["customEvent"] as? Map<String, Any>
                            customEventsSpotChecks = listOf(response.toMap())
                            if(!customEvent.isNullOrEmpty()) {
                                return false
                            }
                        }

                        this.triggerToken = response.triggerToken
                        response.appearance?.let { setAppearance(it) }
                        isChecksPassed = true
                        response.spotCheckId?.also { spotCheckID = it.toDouble()}
                        response.spotCheckContactId?.also { spotCheckContactID = it.toDouble() }
                        spotCheckURL =
                            "https://$domainName/n/spotcheck/$triggerToken?spotcheckContactId=${String.format("%.0f", spotCheckContactID)}&traceId=$traceId&spotcheckUrl=$screenName"

                        return true

                    } else {
                        Log.d(
                            "SPOT-CHECK",
                            "Error: Checks Condition Failed"
                        )
                        return false
                    }
                }

            } else {
                Log.d(
                    "SPOT-CHECK",
                    "CheckPassed Not Received"
                )
            }

            if (response.multiShow != null) {
                if (!isSpotPassed && !isChecksPassed) {
                    val multiShow: Boolean = response.multiShow == true
                    val spotCheckList = response.resultantSpotCheck as? List<Map<String, Any>>
                    if (multiShow && !spotCheckList.isNullOrEmpty()) {
                        spotCheckList?.let { list ->
                            customEventsSpotChecks = list
                        }

                        var selectedSpotCheck: Map<String, Any>? = null
                        var minDelay: Double = Double.POSITIVE_INFINITY

                        customEventsSpotChecks.forEach { spotCheck ->
                            val checks = spotCheck["checks"] as? Map<String, Any>
                            if (checks.isNullOrEmpty()) {
                                spotCheck.also {
                                    selectedSpotCheck =
                                        (it ?: mapOf()) as Map<String, Any>?
                                }
                                return@forEach
                            } else {
                                val afterDelay = checks["afterDelay"] as? String
                                val delay = afterDelay?.toDoubleOrNull() ?: Double.POSITIVE_INFINITY
                                if (minDelay > delay) {
                                    minDelay = delay
                                    spotCheck.also {
                                        selectedSpotCheck = (it ?: mapOf()) as Map<String, Any>?
                                    }
                                }
                            }
                        }

                        selectedSpotCheck?.let { selected ->
                            val checkCondition = selected["checks"] as? Map<String, Any>
                            val afterDelay = checkCondition?.get("afterDelay") as? String
                            val afterDelayDouble = afterDelay?.toDoubleOrNull() ?: 0.0
                            this.afterDelay = afterDelayDouble
                        }
                        this.triggerToken = selectedSpotCheck?.get("triggerToken") as String
                        setAppearance(selectedSpotCheck?.get("appearance") as Map<String, Any> ?: mapOf<String, Any>())
                        spotCheckID = selectedSpotCheck?.get("id") as Double
                        spotCheckContactID = (selectedSpotCheck?.get("spotCheckContact") as Map<String, Any>)?.get("id") as Double


                        spotCheckURL =
                            "https://$domainName/n/spotcheck/$triggerToken?spotcheckContactId=${String.format("%.0f", spotCheckContactID)}&traceId=$traceId&spotcheckUrl=$screenName"

                        return true

                    }
                    else {
                        Log.d(
                            "SPOT-CHECK",
                            "MultiShow Failed"
                        )
                    }
                }

            } else {
                Log.d(
                    "SPOT-CHECK",
                    "MultiShow Not Received"
                )
            }
            return false

        } catch (e: Exception) {
            Log.e("Error @ sendRequestForTrackScreen", e.message, e);
            return false;
        }
    }

    suspend fun sendEventTriggerRequest(screenName: String, event: Map<String, Any>): Boolean {
        var isSpotPassed by mutableStateOf(false)
        var isChecksPassed by mutableStateOf(false)

        try {

            if (customEventsSpotChecks.isEmpty()) {
                Log.d("SPOT-CHECK", "No Events in this screen")
                return false
            } else {
                customEventsSpotChecks.forEach { spotCheck ->
                    val checks = (spotCheck["checks"] as? Map<String, Any>)
                        ?: (spotCheck["checkCondition"] as? Map<String, Any>)
                    val customEvent = checks?.get("customEvent") as? Map<String, Any> ?: emptyMap()

                    val eventName = customEvent["eventName"] as? String

                    if (event.containsKey(eventName)) {
                        val spotcheckid = spotCheck["spotCheckId"]
                        val id = spotcheckid ?: spotCheck["id"]
                        val idDouble = id.toString().toDoubleOrNull()
                        if (idDouble != null) {
                            val formattedId = String.format("%.0f", idDouble)
                            selectedSpotCheckID = Integer.parseInt(formattedId)
                        } else {
                            selectedSpotCheckID = -1
                        }
                        if (selectedSpotCheckID != Int.MAX_VALUE) {

                            val payload = EventRequestPayload(
                                screenName = screenName,
                                variables = variables,
                                customProperties = customProperties,
                                userDetails = UserDetails(
                                    email = email,
                                    firstName = firstName,
                                    lastName = lastName,
                                    phoneNumber = phoneNumber
                                ),
                                visitor = Visitor(
                                    deviceType = "Mobile",
                                    operatingSystem = "Android",
                                    screenResolution = getScreenResolution(),
                                    currentDate = getCurrentDate(),
                                    timezone = TimeZone.getDefault().id
                                ),
                                spotCheckId = selectedSpotCheckID,
                                eventTrigger = mapOf("customEvent" to event),
                                traceId = traceId
                            )

                            val apiService = RetrofitClient.create("https://${domainName}")
                            val response = apiService.sendEventTrigger(targetToken, payload)
                            Log.i("sendEventTriggerRequest", response.toString())

                            if (response.show != null) {
                                val show: Boolean = response.show == true

                                if (show) {
                                    this.triggerToken = response.triggerToken
                                    response.appearance?.let { setAppearance(it) }
                                    isSpotPassed = true
                                    response.spotCheckId?.also { spotCheckID = it.toDouble() }
                                    response.spotCheckContactId?.also {
                                        spotCheckContactID = it.toDouble()
                                    }
                                    spotCheckURL =
                                        "https://$domainName/n/spotcheck/$triggerToken?spotcheckContactId=${String.format("%.0f", spotCheckContactID)}&traceId=$traceId&spotcheckUrl=$screenName"
                                    return true
                                } else {
                                    Log.d(
                                        "SPOT-CHECK",
                                        "Error: Spots or Checks or Visitor or Recurrence Condition Failed"
                                    )
                                }
                            } else {
                                Log.d(
                                    "SPOT-CHECK",
                                    "Error: Show Not Received"
                                )
                            }

                            if (response.eventShow != null) {
                                if (!isSpotPassed) {
                                    val eventShow: Boolean = response.eventShow
                                    if (eventShow) {

                                        val checkCondition =
                                            response.checkCondition as? Map<String, Any>
                                        checkCondition?.let { condition ->
                                            val afterDelay = condition["afterDelay"] as? String
                                            afterDelay?.let { delay ->
                                                val afterDelayDouble =
                                                    delay.toDoubleOrNull() ?: 0.0
                                                this.afterDelay = afterDelayDouble
                                            }
                                            val customEvents =
                                                condition["customEvent"] as? Map<String, Any>
                                            customEvents?.let { event ->
                                                val delayInSeconds =
                                                    event["delayInSeconds"] as? String
                                                delayInSeconds?.let { delay ->
                                                    val afterDelayDouble =
                                                        delay.toDoubleOrNull() ?: 0.0
                                                    this.afterDelay = afterDelayDouble
                                                }
                                            }
                                        }

                                        this.triggerToken = response.triggerToken
                                        response.appearance?.let { setAppearance(it) }
                                        isChecksPassed = true
                                        response.spotCheckId?.also { spotCheckID = it.toDouble() }
                                        response.spotCheckContactId?.also {
                                            spotCheckContactID = it.toDouble()
                                        }
                                        spotCheckURL =
                                            "https://$domainName/n/spotcheck/$triggerToken?spotcheckContactId=${String.format("%.0f", spotCheckContactID)}&traceId=$traceId&spotcheckUrl=$screenName"

                                        return true
                                    } else {
                                        Log.d(
                                            "SPOT-CHECK",
                                            "Error: EventShow Failed"
                                        )
                                    }
                                }
                            } else {
                                Log.d(
                                    "SPOT-CHECK",
                                    "Error: EventShow not Received"
                                )
                            }
                        }
                    }


                }
            }

            return false;

        } catch (e: Exception) {
            Log.e("Error @ sendEventTriggerRequest", e.message, e)
            return false;
        }
    }

    fun onClose() {
        isVisible = false
        spotCheckID = 0.0
        position = ""
        currentQuestionHeight = 0.0
        spotCheckContactID = 0.0
        spotCheckURL = ""
    }

    fun openSpot() {
        isVisible = true
    }

    private suspend fun getScreenResolution(): ScreenResolution {
        return withContext(Dispatchers.IO) {
            val width = Resources.getSystem().displayMetrics.widthPixels
            val height = Resources.getSystem().displayMetrics.heightPixels
            ScreenResolution(width, height)
        }
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun setAppearance(appearance: Map<String, Any>) {
        val position = appearance["position"] as? String
        val isCloseButtonEnabled = appearance["closeButton"] as? Boolean
        val cardProp = appearance["cardProperties"] as? Map<String, Any>
        val colors = appearance["colors"] as? Map<String, Any>
        val overrides = colors?.get("overrides") as? Map<String, String>

        position?.let { pos ->
            if (pos == "top_full") this.position = "top"
            else if (pos == "center_center") this.position = "center"
            else if (pos == "bottom_full") this.position = "bottom"
        }

        val mxHeight = cardProp?.get("maxHeight") as? Double
            ?: (cardProp?.get("maxHeight") as? String)?.toDouble() ?: 1.0
        this.maxHeight = mxHeight / 100

        this.isFullScreenMode = appearance["mode"] as? String == "fullScreen"

        val bannerImage = appearance["bannerImage"] as? Map<String, Any>
        bannerImage?.let { banner ->
            this.isBannerImageOn = banner["enabled"] as? Boolean ?: false
        }
    }
}

fun generateTraceId(): String {
    val uuid = UUID.randomUUID()
    val timestamp = System.currentTimeMillis()
    return "$uuid-$timestamp"
}

suspend fun trackScreen(screen: String, config: SpotCheckConfig) {
    val response = config.sendRequestForTrackScreen(screen)
    if(response) {
        val delayMillis = (config.afterDelay * 1000).toLong()
        Handler(Looper.getMainLooper()).postDelayed({
            config.openSpot()
            Log.i("TrackScreen", config.isVisible.toString())
        }, delayMillis)
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
