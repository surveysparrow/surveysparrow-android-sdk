package com.surveysparrow.sdk.example.movie_review_kotlin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.surveysparrow.ss_android_sdk.SsSurvey
import com.surveysparrow.ss_android_sdk.SurveySparrow


class PlaybackActivity : AppCompatActivity() {
    private val RATE_MOVIE_REQUEST = 102
    val LOG_TAG = "MOVIE_APP_TOAST"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
    }

    fun rateMovie(view: View) {
        // Create a survey
        val survey = SsSurvey("your_domain", "your_survey_token")
            .addCustomParam("movieName", "Sparrow Life") // Pass data to the survey from your code

        // Create SurveySparrow to set configs and trigger survey.
        val surveySparrow = SurveySparrow(this, survey)
            .setWaitTime(2000) // How long you should display thank you page

        // Start survey as an Activity for result
        surveySparrow.startSurveyForResult(RATE_MOVIE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == RATE_MOVIE_REQUEST) {
            if(resultCode == Activity.RESULT_OK) {
                // Get the survey response
                val response = SurveySparrow.toJSON(data?.data.toString())
                val responseData = response.getJSONArray("response")
                val rating = responseData.getJSONObject(0).getInt("answer")

                // Show a recommendation page if the user give 4+ rating
                if(rating >= 4) {
                    startActivity(Intent(this, RecommendationsActivity::class.java))
                }
            } else {
                Log.v(LOG_TAG, "Review survey not completed!")
            }
        }
    }
}
