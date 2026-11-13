package com.surveysparrow.surveysparrow_android_sdk.internal.adapters

import com.surveysparrow.surveysparrow_android_sdk.SsSpotcheckListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ListenerAdapter {
    var listener: SsSpotcheckListener? = null

    fun onSurveyLoaded(response: Map<String, Any>) {
        CoroutineScope(Dispatchers.IO).launch {
            listener?.onSurveyLoaded(response)
        }
    }

    fun onSurveyResponse(response: Map<String, Any>) {
        CoroutineScope(Dispatchers.IO).launch {
            listener?.onSurveyResponse(response)
        }
    }

    fun onPartialSubmission(response: Map<String, Any>) {
        CoroutineScope(Dispatchers.IO).launch {
            listener?.onPartialSubmission(response)
        }
    }

    fun onCloseButtonTap() {
        CoroutineScope(Dispatchers.IO).launch {
            listener?.onCloseButtonTap()
        }
    }
}
