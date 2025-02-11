package com.example.kotlin_example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kotlin_example.ui.theme.KotlinExampleTheme
import com.surveysparrow.ss_android_sdk.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), OnSsResponseEventListener, OnSsValidateSurveyEventListener {
    companion object {
        const val SURVEY_REQUEST_CODE = 1
        const val LOG_TAG = "SS_SAMPLE"
        const val SS_DOMAIN = "gokulkrishnaraju1183.surveysparrow.com"
        const val SS_TOKEN = "tt-ChaSVQWRDF7"
        const val SS_LANG_CODE = "en"
    }

    private lateinit var survey: SsSurvey
    private lateinit var surveySparrow: SurveySparrow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KotlinExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        Button(onClick = { startSurvey() }) {
                            Text("Start Survey")
                        }
                    }
                }
            }
        }
    }

    private fun startSurvey() {
        val params = arrayOf(
            SsSurvey.CustomParam("emailaddress", "email@surveysparrow.com"),
            SsSurvey.CustomParam("email", "email@surveysparrow.com"),
            SsSurvey.CustomParam("url", "a")
        )
        val properties = hashMapOf("langCode" to SS_LANG_CODE)
        survey = SsSurvey(SS_DOMAIN, SS_TOKEN, params, properties)
        surveySparrow = SurveySparrow(this, survey)
            .setActivityTheme(R.style.Theme_KotlinExample)
            .enableBackButton(true)
            .setWaitTime(2000)
            .setStartAfter(TimeUnit.DAYS.toMillis(3))
            .setRepeatInterval(TimeUnit.DAYS.toMillis(5))
            .setRepeatType(SurveySparrow.REPEAT_TYPE_CONSTANT)
            .setFeedbackType(SurveySparrow.SINGLE_FEEDBACK)
        surveySparrow.setValidateSurveyListener(this)
        surveySparrow.startSurvey(SURVEY_REQUEST_CODE)
    }

    override fun onSsResponseEvent(response: org.json.JSONObject) {
        Log.v(LOG_TAG, response.toString())
    }

    override fun onSsValidateSurvey(response: org.json.JSONObject) {
        Log.v(LOG_TAG, "survey validation error json$response")
    }
}
