package com.surveysparrow.surveysparrow_android_sdk

interface SsSpotcheckListener {

    suspend fun onSurveyLoaded(response: Map<String, Any>) {}


    suspend fun onSurveyResponse(response: Map<String, Any>) {}


    suspend fun onPartialSubmission(response: Map<String, Any>) {}


    suspend fun onCloseButtonTap() {}
}
