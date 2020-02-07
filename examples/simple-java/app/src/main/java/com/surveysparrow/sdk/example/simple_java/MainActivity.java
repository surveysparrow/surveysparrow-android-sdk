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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements OnSsResponseEventListener {
    public static final int SURVEY_REQUEST_CODE = 1;
    public static final int SURVEY_SCHEDULE_REQUEST_CODE = 2;
    public static final String LOG_TAG = "SS_SAMPLE";

    public static final String SS_DOMAIN = "Add your domain here";
    public static final String SS_TOKEN = "Add your survey token here";

    SsSurvey survey;
    SurveySparrow surveySparrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        survey = new SsSurvey(SS_DOMAIN, SS_TOKEN);
        surveySparrow = new SurveySparrow(this, survey)
                .setActivityTheme(R.style.AppTheme)
                .setAppBarTitle(R.string.app_name)
                .enableBackButton(true)
                .setWaitTime(2000)

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
        FragmentManager fragmentManager = this.getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        SsSurveyFragment surveyFragment = new SsSurveyFragment(survey);
        fragmentTransaction.add(R.id.surveyContainer, surveyFragment);
        fragmentTransaction.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SURVEY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                JSONObject responses = convertToJSON(data.getData().toString());
                Log.v(LOG_TAG, responses.toString());
            } else {
                Log.v(LOG_TAG, "No Response");
            }
        }
    }

    private JSONObject convertToJSON(String jsonString) {
        JSONObject json;
        try {
            json = new JSONObject(jsonString);
        } catch (JSONException e) {
            Log.v(LOG_TAG, "JSON parse error");
            return null;
        }
        return json;
    }

    @Override
    public void onSsResponseEvent(String s) {
        Log.v(LOG_TAG, s);
    }
}
