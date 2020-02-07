package com.surveysparrow.sdk.example.simple_java;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.surveysparrow.ss_android_sdk.OnSsResponseEventListener;
import com.surveysparrow.ss_android_sdk.SsSurvey;
import com.surveysparrow.ss_android_sdk.SsSurveyFragment;
import com.surveysparrow.ss_android_sdk.SurveySparrow;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements OnSsResponseEventListener {
    public static final int SURVEY_REQUEST_CODE = 1;
    public static final int SURVEY_SCHEDULE_REQUEST_CODE = 2;
    public static final String LOG_TAG = "SS_SAMPLE";

    /**
     * Domain of your SurveySparrow account.
     */
    public static final String SS_DOMAIN = "Add_your_domain_here";

    /**
     * Mobile SDK token of your survey.
     * You can generate a Mobile SDK token in your survey's share page.
     */
    public static final String SS_TOKEN = "Add_your_survey_token_here";

    SsSurvey survey;
    SurveySparrow surveySparrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create a SsSurvey object with your domain & survey token.
        survey = new SsSurvey(SS_DOMAIN, SS_TOKEN);

        // You only need SurveySparrow object if you want to open the survey in an Activity or schedule it.
        surveySparrow = new SurveySparrow(this, survey)
                .setActivityTheme(R.style.AppTheme)
                .setAppBarTitle(R.string.app_name)
                .enableBackButton(true)
                .setWaitTime(2000)

                // Only for scheduling
                .setStartAfter(TimeUnit.DAYS.toMillis(3L))
                .setRepeatInterval(TimeUnit.DAYS.toMillis(5L))
                .setRepeatType(SurveySparrow.REPEAT_TYPE_CONSTANT)
                .setFeedbackType(SurveySparrow.SINGLE_FEEDBACK);

        surveySparrow.scheduleSurvey(SURVEY_SCHEDULE_REQUEST_CODE);
    }

    public void startSurveyActivity(View v) {
        surveySparrow.startSurveyForResult(SURVEY_REQUEST_CODE);
    }

    public void showSurveyFragment(View v) {
        SsSurveyFragment surveyFragment = new SsSurveyFragment(survey);

        // Add the SsSurveyFragment to the Activity.
        FragmentManager fragmentManager = this.getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.surveyContainer, surveyFragment);
        fragmentTransaction.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SURVEY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                JSONObject responses = SurveySparrow.toJSON(data.getData().toString());
                Log.v(LOG_TAG, responses.toString());
            } else {
                Log.v(LOG_TAG, "No Response");
            }
        }
    }

    @Override
    public void onSsResponseEvent(JSONObject s) {
        Log.v(LOG_TAG, s.toString());
    }
}
