package com.surveysparrow.surveysparrow_android_sdk

import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SpotCheckConfig(
    var domainName: String,
    var targetToken: String,
    private var userDetails: HashMap<String, String>,
    private var variables: Map<String, Any> = mapOf(),
    private var customProperties: Map<String, Any> = mapOf(),
    var preferences: SharedPreferences? = null
) {
    var position by mutableStateOf("")
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

    init {
        if ( traceId.isEmpty() ) {
            traceId = generateTraceId()
        }
    }

    suspend fun sendRequestForTrackScreen(screenName: String): Boolean {
        var isSpotPassed by mutableStateOf(false)
        var isChecksPassed by mutableStateOf(false)

        Log.d("SpotCheck", "sendRequestForTrackScreen: ")

        if ( preferences != null && userDetails["uuid"] == null && userDetails["email"] == null && userDetails["mobile"] == null ) {
            this.preferences!!.getString("SurveySparrowUUID" , null).also { userDetails["uuid"] = it as String }
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
            val response = apiService.fetchProperties(targetToken, payload)
            Log.i("sendRequestForTrackScreen", response.toString())

            if (preferences != null){
                val editor = preferences!!.edit()
                editor.putString("SurveySparrowUUID", response.uuid)
                editor.apply()
            }

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

                            if ( preferences != null && userDetails["uuid"] == null && userDetails["email"] == null && userDetails["mobile"] == null ) {
                                this.preferences!!.getString("SurveySparrowUUID" , null).also { userDetails["uuid"] = it as String }
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
        this.isCloseButtonEnabled = isCloseButtonEnabled ?: false

        val mxHeight = cardProp?.get("maxHeight") as? Double
            ?: (cardProp?.get("maxHeight") as? String)?.toDouble() ?: 1.0
        this.maxHeight = mxHeight / 100

        this.isFullScreenMode = appearance["mode"] as? String == "fullScreen"

        val bannerImage = appearance["bannerImage"] as? Map<String, Any>
        bannerImage?.let { banner ->
            this.isBannerImageOn = banner["enabled"] as? Boolean ?: false
        }
        overrides?.let { closeButtonStyle = it }
    }
}
