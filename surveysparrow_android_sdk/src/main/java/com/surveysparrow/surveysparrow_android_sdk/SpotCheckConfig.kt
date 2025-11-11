package com.surveysparrow.surveysparrow_android_sdk
import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.os.Build

class SpotCheckConfig(
    var domainName: String,
    var targetToken: String,
    private var userDetails: HashMap<String, String>,
    private var variables: Map<String, Any> = mapOf(),
    private var customProperties: Map<String, Any> = mapOf(),
    var preferences: SharedPreferences? = null,
    var spotCheckListener: SsSpotcheckListener? = null
) {
    var position by mutableStateOf("bottom")
    var spotCheckURL by mutableStateOf("")
    var triggerToken by mutableStateOf("")
    var spotCheckID by mutableDoubleStateOf(0.0)
    var spotCheckContactID by mutableDoubleStateOf(0.0)
    private var selectedSpotCheckID by mutableIntStateOf(0)

    var maxHeight by mutableDoubleStateOf(0.5)
    var afterDelay by mutableDoubleStateOf(0.0)
    var currentQuestionHeight by mutableDoubleStateOf(0.0)

    var isVisible by mutableStateOf(false)
    var isFullScreenMode by mutableStateOf(false)
    var isBannerImageOn by mutableStateOf(false)
    var isCloseButtonEnabled by mutableStateOf(false)

    var closeButtonStyle by mutableStateOf<Map<String, String?>>(mapOf())
    private var customEventsSpotChecks by mutableStateOf<List<Map<String, Any?>>>(listOf(mapOf()))
    var traceId: String = ""
    var filteredSpotChecks: List<FilteredSpotCheck>? by mutableStateOf(null)
    var classicUrl: String by mutableStateOf("")
    var chatUrl: String by mutableStateOf("")
    var spotCheckType: String by mutableStateOf("")
    var spotChecksMode: String by mutableStateOf("")
    var avatarUrl: String by mutableStateOf("")
    var avatarEnabled: Boolean by mutableStateOf(false)
    var isClassicLoading by  mutableStateOf(true)
    var isChatLoading by  mutableStateOf(true)
    var isInjected by  mutableStateOf(true)
    var isMounted by mutableStateOf(false)
    var classicWebViewRef by  mutableStateOf<WebView?>(null)
    var chatWebViewRef by  mutableStateOf<WebView?>(null)
    var activity: Activity? = null
    var originalSoftInputMode by mutableStateOf<Int?>(null)
    var isSpotCheckButton by  mutableStateOf(false)
    var spotCheckButtonConfig by mutableStateOf<Map<String, Any>>(mapOf())
    var showSurveyContent by mutableStateOf(true)
    var isThankyouPageSubmission by mutableStateOf(false)

    init {
        if (traceId.isEmpty()) {
            traceId = generateTraceId()
        }
        CoroutineScope(Dispatchers.IO).launch {
            initializeWidget()
        }
    }

    private fun isChatSurvey(type: String): Boolean {
        return type == "Conversational" ||
                type == "CESChat" ||
                type == "NPSChat" ||
                type == "CSATChat"
    }


    private suspend fun initializeWidget() {
        try {
            if (targetToken.isNotEmpty() && domainName.isNotEmpty()) {
                val api = RetrofitClient.create("https://$domainName")
                val response = api.getInitData(targetToken)

                var classicIframe = false
                var chatIframe = false

                response.filteredSpotChecks.let { spotChecks: List<FilteredSpotCheck>? ->
                    filteredSpotChecks = spotChecks

                    spotChecks?.forEach { spotcheck ->
                        val mode = spotcheck.appearance?.get("mode") as? String
                        val surveyType = spotcheck.survey?.get("surveyType") as? String

                        when (mode) {
                            "card", "miniCard" -> classicIframe = true
                            "fullScreen" -> {
                                if (isChatSurvey(surveyType.toString())) {
                                    chatIframe = true
                                } else {
                                    classicIframe = true
                                }
                            }
                        }
                    }
                }

                chatUrl = if (chatIframe) "https://$domainName/eui-template/chat?isSpotCheck=true" else ""
                classicUrl = if (classicIframe) "https://$domainName/eui-template/classic?isSpotCheck=true" else ""
            }
        } catch (e: Exception) {
            Log.e("SpotCheckConfig", "Error initializing widget", e)
        }
    }


    suspend fun sendRequestForTrackScreen(screenName: String): Boolean {
        var isSpotPassed by mutableStateOf(false)
        var isChecksPassed by mutableStateOf(false)

        if ( this.preferences != null && userDetails["uuid"] == null && userDetails["email"] == null && userDetails["mobile"] == null ) {
            this.preferences?.getString("SurveySparrowUUID" , null).also {
                val tuuid = it ;
                if(!tuuid.isNullOrEmpty()) {
                    userDetails["uuid"] = tuuid.toString()
                }
            }
        }

        try {
            val payload = PropertiesRequestPayload(
                screenName = screenName,
                variables = variables,
                customProperties = customProperties,
                userDetails = userDetails,
                visitor = Visitor(
                    deviceType = "MOBILE",
                    operatingSystem = "Android",
                    screenResolution = getScreenResolution(),
                    currentDate = getCurrentDate(),
                    timezone = TimeZone.getDefault().id
                ),
                traceId = traceId
            )
            customEventsSpotChecks = listOf(mapOf())

            val apiService = RetrofitClient.create("https://${domainName}")
            val response = apiService.fetchProperties(targetToken, "ANDROID", true, payload)
            Log.i("sendRequestForTrackScreen", response.toString())

            if (preferences != null && response.uuid != null) {
                val editor = preferences!!.edit()
                editor.putString("SurveySparrowUUID", response.uuid)
                editor.apply()
            }

            if (response.show != null) {

                val show: Boolean = response.show

                if (show) {
                    this.triggerToken = response.triggerToken
                    isSpotPassed = true
                    response.spotCheckId?.also { spotCheckID = it.toDouble() }
                    response.spotCheckContactId?.also { spotCheckContactID = it.toDouble() }

                    response.appearance?.let { setAppearance(it, screenName) }
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
                            val afterDelay = condition["afterDelay"] as? Double
                            afterDelay?.let { delay ->
                                this.afterDelay = delay
                            }
                            val customEvent = condition["customEvent"] as? Map<String, Any>
                            customEventsSpotChecks = listOf(response.toMap())
                            if (!customEvent.isNullOrEmpty()) {
                                return false
                            }
                        }

                        this.triggerToken = response.triggerToken

                        isChecksPassed = true
                        response.spotCheckId?.also { spotCheckID = it.toDouble() }
                        response.spotCheckContactId?.also { spotCheckContactID = it.toDouble() }
                        response.appearance?.let { setAppearance(it, screenName) }
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

                        for (spotCheck in customEventsSpotChecks) {
                            val checks = spotCheck["checks"] as? Map<String, Any>
                            if (checks.isNullOrEmpty()) {
                                selectedSpotCheck = (spotCheck ?: mapOf()) as Map<String, Any>?
                            } else {
                                val afterDelay = checks["afterDelay"] as? Double
                                val delay = afterDelay ?: Double.POSITIVE_INFINITY
                                if (minDelay > delay) {
                                    minDelay = delay
                                    selectedSpotCheck = (spotCheck ?: mapOf()) as Map<String, Any>?
                                }
                            }
                        }

                        selectedSpotCheck?.let { selected ->
                            val checkCondition = selected["checks"] as? Map<String, Any>
                            val afterDelay = checkCondition?.get("afterDelay") as? Double
                            if (afterDelay != null) {
                                this.afterDelay = afterDelay
                            }

                            this.triggerToken = selected["triggerToken"] as String

                            spotCheckID = selected["id"] as Double
                            spotCheckContactID =
                                (selected["spotCheckContact"] as Map<String, Any>)["id"] as Double

                            setAppearance(
                                selected["appearance"] as Map<String, Any>,
                                screenName
                            )
                            return true
                        }

                    } else {
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

                            if (this.preferences != null && userDetails["uuid"] == null && userDetails["email"] == null && userDetails["mobile"] == null) {
                                this.preferences?.getString("SurveySparrowUUID", null).also {
                                    val tuuid = it;
                                    if (!tuuid.isNullOrEmpty()) {
                                        userDetails["uuid"] = tuuid.toString()
                                    }
                                }
                            }

                            val payload = EventRequestPayload(
                                screenName = screenName,
                                variables = variables,
                                customProperties = customProperties,
                                userDetails = userDetails,
                                visitor = Visitor(
                                    deviceType = "MOBILE",
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
                            val response = apiService.sendEventTrigger(targetToken, true, payload)
                            Log.i("sendEventTriggerRequest", response.toString())

                            if (response.show != null) {
                                val show: Boolean = response.show == true

                                if (show) {
                                    this.triggerToken = response.triggerToken
                                    isSpotPassed = true
                                    response.spotCheckId?.also { spotCheckID = it.toDouble() }
                                    response.spotCheckContactId?.also {
                                        spotCheckContactID = it.toDouble()
                                    }
                                    response.appearance?.let { setAppearance(it, screenName) }

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
                                            val afterDelay = condition["afterDelay"] as? Double
                                            afterDelay?.let { delay ->
                                                this.afterDelay = delay
                                            }
                                            val customEvents =
                                                condition["customEvent"] as? Map<String, Any>
                                            customEvents?.let { event ->
                                                val delayInSeconds =
                                                    event["delayInSeconds"] as? Double
                                                delayInSeconds?.let { delay ->
                                                    this.afterDelay = delay
                                                }
                                            }
                                        }

                                        this.triggerToken = response.triggerToken
                                        isChecksPassed = true
                                        response.spotCheckId?.also { spotCheckID = it.toDouble() }
                                        response.spotCheckContactId?.also {
                                            spotCheckContactID = it.toDouble()
                                        }

                                        response.appearance?.let { setAppearance(it, screenName) }
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
        val targetWebView = if (spotCheckType == "chat") chatWebViewRef else classicWebViewRef

        val jsToInject = """
        (function() {
            window.dispatchEvent(new MessageEvent('message', {
                data: {"type":"UNMOUNT_APP"}
            }));
        })();
    """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    targetWebView?.evaluateJavascript(jsToInject, null)
                }
            } catch (e: Exception) {
                Log.e("SpotCheck", "Exception: ${e.message}", e)
            }
        }

        isVisible = false
        isFullScreenMode = false
        spotCheckID = 0.0;
        position = "bottom"
        currentQuestionHeight = 0.0
        isCloseButtonEnabled = false
        spotCheckContactID = 0.0
        spotCheckURL = ""
        isMounted = false
        isInjected = false
        spotChecksMode = ""
        avatarEnabled = false
        avatarUrl =  ""
        if (isSpotCheckButton) {
            showSurveyContent = true
        }
        isSpotCheckButton = false
        isThankyouPageSubmission = false
        activity?.runOnUiThread {
            originalSoftInputMode?.let {
                activity?.window?.setSoftInputMode(it)
            }
        }


    }

    fun openSpot() {
        activity?.runOnUiThread {
            activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
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


    private suspend fun getUserAgent(): String {
        var userAgent = "Mozilla/5.0 "

        val displayMetrics = Resources.getSystem().displayMetrics
        val devicePixelRatio = displayMetrics.density.toInt()
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val isTablet = when {
            devicePixelRatio < 2 && (width >= 1000 || height >= 1000) -> true
            devicePixelRatio == 2 && (width >= 1920 || height >= 1920) -> true
            else -> false
        }

        val deviceType = if (isTablet) "Tablet" else "Mobile"
        val androidVersion = Build.VERSION.RELEASE

        userAgent += "(Linux; Android $androidVersion; $deviceType) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36"

        return userAgent
    }


    suspend fun closeSpotCheck() {
        try {
            val apiService = RetrofitClient.create("https://${domainName}")
            val payload = DismissPayload(
                traceId = traceId,
                triggerToken = triggerToken
            )

            val response =
                apiService.closeSpotCheck(spotCheckContactID = String.format("%.0f", spotCheckContactID), payload = payload)
            if (response.success == true) {
                CoroutineScope(Dispatchers.IO).launch {
                    spotCheckListener?.onCloseButtonTap()
                }

                Log.i("SPOT-CHECK", "CloseSpotCheck: Success")
            }
        } catch (e: Exception) {
            Log.i("SPOT-CHECK", "$e")
        }

    }

    private fun setAppearance(responseJson: Map<String, Any>, screen: String) {
        if (responseJson.isEmpty()) return

        val appearance = responseJson as? Map<String, Any> ?: emptyMap()
        val currentSpotCheck = filteredSpotChecks?.find { spotcheck: FilteredSpotCheck ->
            val id = spotcheck.id

            id == spotCheckID.toInt()
        }



        val chat = ((currentSpotCheck?.survey)?.get("surveyType") as? String)?.let {
            isChatSurvey(
                it
            )
        } == true && appearance["mode"] == "fullScreen"

        spotChecksMode = (appearance["mode"] as? String).toString()
        avatarEnabled = (appearance["avatar"] as? Map<*, *>)?.get("enabled") as? Boolean ?: false
        avatarUrl = (appearance["avatar"] as? Map<*, *>)?.get("avatarUrl") as? String ?: ""
        spotCheckType = if (chat) "chat" else "classic"
        isSpotCheckButton = (appearance["type"] == "spotcheckButton")
        spotCheckButtonConfig = if (isSpotCheckButton) (currentSpotCheck?.appearance?.get("buttonConfig") ?: mapOf<String, Any>())
                as Map<String, Any> else {
            mapOf<String, Any>()
        }
        showSurveyContent = !isSpotCheckButton
        when (appearance["position"]) {
            "top_full" -> position = "top"
            "center_center" -> position = "center"
            "bottom_full" -> position = "bottom"
        }


        isCloseButtonEnabled = appearance["closeButton"] as? Boolean ?: false
        closeButtonStyle = (appearance["colors"] as? Map<*, *>)?.get("overrides") as? Map<String, String> ?: emptyMap()

        (appearance["cardProperties"] as? Map<*, *>)?.get("maxHeight")?.let {
            maxHeight = it.toString().toDoubleOrNull()?.div(100) ?: 1.0
        }

        isFullScreenMode = appearance["mode"] == "fullScreen"

        isBannerImageOn = (appearance["bannerImage"] as? Map<*, *>)?.get("enabled") as? Boolean ?: false

        val sb = StringBuilder("https://$domainName/n/spotcheck/$triggerToken/${if (chat) "config" else "bootstrap"}")
        sb.append("?spotcheckContactId=${String.format("%.0f", spotCheckContactID)}")
        sb.append("&traceId=$traceId")
        sb.append("&spotcheckUrl=$screen")

        variables.forEach { (key, value) -> sb.append("&$key=$value") }
        val fullUrl = sb.toString()
        spotCheckURL = fullUrl
        CoroutineScope(Dispatchers.IO).launch {

            try {
                val apiService = RetrofitClient.create("https://$domainName")
                val userAgent = getUserAgent()
                Log.d("useragent", userAgent)
                val response = apiService.contentApi(spotCheckURL, userAgent)


                val responseBodyString = response.body()?.string()
                val gson = Gson()
                val dataType = object : TypeToken<Map<String, Any?>>() {}.type
                val data: Map<String, Any?> = gson.fromJson(responseBodyString, dataType)



                val config = data["config"] as? Map<*, *>
                    val themeInfo = config?.get("generatedCSS")

                    val themePayload = mapOf(
                        "type" to "THEME_UPDATE_SPOTCHECK",
                        "themeInfo" to themeInfo
                    )

                    val payload = mapOf(
                        "type" to "RESET_STATE",
                        "state" to mapOf<String, Any?>(
                            *(data.map { it.key to it.value }.toTypedArray()),
                            "skip" to true,
                            "spotCheckAppearance" to appearance + ("targetType" to "MOBILE"),
                            "spotcheckUrl" to screen,
                            "traceId" to traceId,
                            "elementBuilderParams" to (variables ?: emptyMap())
                        ) as Map<String, Map<String, Any>>
                    )




                    val js = """
                    (function() {
                        window.dispatchEvent(new MessageEvent('message', { data: ${gson.toJson(payload)} }));
                        window.dispatchEvent(new MessageEvent('message', { data: ${gson.toJson(themePayload)} }));
                    })();
                """.trimIndent()



                    withContext(Dispatchers.Main) {
                        val webView = if (chat) chatWebViewRef else classicWebViewRef
                        val isLoading = if (chat) isChatLoading else isClassicLoading

                        if (!isLoading) {
                            webView?.evaluateJavascript(js, null)
                            isInjected = true
                        } else {
                            snapshotFlow { isClassicLoading }
                                .filter { !it }
                                .first()
                                .let {
                                    webView?.evaluateJavascript(js, null)
                                    isInjected = true
                                }

                        }
                    }

            } catch (e: Exception) {
                Log.e("SpotCheck", "Exception: ${e.message}", e)
            }
        }
    }
}
