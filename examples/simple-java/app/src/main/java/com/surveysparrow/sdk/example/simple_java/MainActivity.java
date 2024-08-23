package com.surveysparrow.sdk.example.simple_java;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.surveysparrow.ss_android_sdk.OnSsResponseEventListener;
import com.surveysparrow.ss_android_sdk.OnSsValidateSurveyEventListener;
import com.surveysparrow.ss_android_sdk.SsSurvey;
import com.surveysparrow.ss_android_sdk.SsSurveyFragment;
import com.surveysparrow.ss_android_sdk.SurveySparrow;
import com.surveysparrow.ss_android_sdk.SsSurvey.CustomParam;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements OnSsResponseEventListener, OnSsValidateSurveyEventListener {
    public static final int SURVEY_REQUEST_CODE = 1;
    public static final int SURVEY_SCHEDULE_REQUEST_CODE = 2;
    public static final String LOG_TAG = "SS_SAMPLE";

    /**
     * Domain of your SurveySparrow account.
     */
    public static String SS_DOMAIN = "your-domain";

    /**
     * Mobile SDK token of your survey.
     * You can generate a Mobile SDK token in your survey's share page.
     */
    public static String SS_TOKEN = "your-survey-token";

    public static String SS_LANG_CODE = "PREFERRED_LANG_CODE" ;

    public static Boolean CLOSE_BUTTON_ENABLED = true ;

    SsSurvey survey;
    SurveySparrow surveySparrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Find the EditText fields
        EditText token = findViewById(R.id.token);
        EditText domain = findViewById(R.id.domain);
        EditText langCode = findViewById(R.id.language);
        EditText closeBtn = findViewById(R.id.closeBtn);

        // Add TextWatchers to the EditText fields to update SS_DOMAIN and SS_TOKEN
        token.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                SS_TOKEN = editable.toString();
                Log.d("SS_TOKEN", SS_TOKEN);
            }
        });

        langCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                SS_LANG_CODE = editable.toString();
                Log.d("SS_LANG_CODE", SS_LANG_CODE);
            }
        });
        closeBtn.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                String temp = editable.toString();
                CLOSE_BUTTON_ENABLED = temp.equals("1");
                Log.d("CLOSE_BUTTON_ENABLED", CLOSE_BUTTON_ENABLED.toString());
            }
        });

        domain.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                SS_DOMAIN = editable.toString();
                Log.d("SS_DOMAIN", SS_DOMAIN);
            }
        });
    }

    public void startSurveyActivity(View v) {
        surveySparrow.startSurveyForResult(SURVEY_REQUEST_CODE);
    }

    public void startSurvey(View v) {
        CustomParam[] params = {
                new CustomParam("emailaddress", "email@surveysparrow.com"),
                new CustomParam("email", "email@surveysparrow.com"),
                new CustomParam("url", "a"),
        };

         // Add Language code in the properties HashMap and pass it to SsSurvey
        HashMap properties = new HashMap<String,String>();
        properties.put("langCode", SS_LANG_CODE);
        properties.put("isCloseButtonEnabled", CLOSE_BUTTON_ENABLED);

        // Create a SsSurvey object with your domain & survey token.
        survey = new SsSurvey(SS_DOMAIN, SS_TOKEN, params, properties);

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
        surveySparrow.setValidateSurveyListener(this);
        surveySparrow.startSurvey(SURVEY_REQUEST_CODE);
    }

    public void showSurveyFragment(View v) {
        CustomParam[] params = {
                new CustomParam("emailaddress", "email@surveysparrow.com"),
                new CustomParam("email", "email@surveysparrow.com"),
                new CustomParam("url", "a"),
        };

        // Add Language code in the properties HashMap and pass it to SsSurvey
        HashMap properties = new HashMap<String,String>();
        properties.put("langCode", SS_LANG_CODE);

        // Create a SsSurvey object with your domain & survey token.
        survey = new SsSurvey(SS_DOMAIN, SS_TOKEN, params, properties);

        SsSurveyFragment surveyFragment = new SsSurveyFragment();
        surveyFragment.setValidateSurveyListener(this);
        surveyFragment.setSurvey(survey);

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

    @Override
    public void onSsValidateSurvey(JSONObject s) {
        Log.v(LOG_TAG, "survey validation error json" + s.toString());
    }
}