interface SsSpotcheckListener {
    suspend fun onSurveyLoaded(response: Map<String, Any>)
    suspend fun onSurveyResponse(response: Map<String, Any>)
    suspend fun onCloseButtonTap()
}
