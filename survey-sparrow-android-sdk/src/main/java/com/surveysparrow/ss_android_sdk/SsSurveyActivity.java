package com.surveysparrow.ss_android_sdk;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.util.Log;

import org.json.JSONObject;

public final class SsSurveyActivity extends AppCompatActivity implements OnSsResponseEventListener, OnSsCloseSurveyEventListener {
    private SsSurvey survey;
    private int activityTheme;
    private CharSequence appbarTitle;
    private boolean enableButton;
    private long waitTime;
    public static final String SS_RT_EXCEPTION_LOG = "SS_RT_EXCEPTION_LOG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            Intent intent = getIntent();
            activityTheme = intent.getIntExtra(SurveySparrow.SS_ACTIVITY_THEME, R.style.SurveyTheme);
            appbarTitle = intent.getStringExtra(SurveySparrow.SS_APPBAR_TITLE);
            int widgetContactId = getIntent().getIntExtra("widgetContactId", 0);
            enableButton = intent.getBooleanExtra(SurveySparrow.SS_BACK_BUTTON, true);
            waitTime = intent.getLongExtra(SurveySparrow.SS_WAIT_TIME, SurveySparrow.SS_DEFAULT_WAIT_TIME);
            survey = (SsSurvey) intent.getSerializableExtra(SurveySparrow.SS_SURVEY);
  
            setTheme(activityTheme);
            setContentView(R.layout.activity_ss_survey);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(appbarTitle);
                actionBar.setDisplayHomeAsUpEnabled(enableButton);
            }

            if (savedInstanceState == null) {
                FragmentManager fragmentManager = getSupportFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                SsSurveyFragment surveyFragment = new SsSurveyFragment();
                surveyFragment.setCloseSurveyListener(this);
                surveyFragment.setData(widgetContactId);
                surveyFragment.setSurvey(survey);
                fragmentTransaction.add(R.id.surveyContainer, surveyFragment, "SURVEY_FRAGMENT_TAG");
                fragmentTransaction.commit();
            } else {
                FragmentManager fragmentManager = getSupportFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                SsSurveyFragment surveyFragment = (SsSurveyFragment) getSupportFragmentManager().findFragmentByTag("SURVEY_FRAGMENT_TAG");
                surveyFragment.setSurvey(survey);
                surveyFragment.setCloseSurveyListener(this);
                surveyFragment.setData(widgetContactId);
                fragmentTransaction.replace(R.id.surveyContainer, surveyFragment, "SURVEY_FRAGMENT_TAG");
                fragmentTransaction.commit();
            }
        } catch (Exception e) {
            Log.e(SS_RT_EXCEPTION_LOG, e.getStackTrace().toString());
        }
    }

    @Override
    public void onSsResponseEvent(JSONObject data) {
        Intent resultIntent = new Intent();
        resultIntent.setData(Uri.parse(data.toString()));
        setResult(RESULT_OK, resultIntent);

        SurveySparrow.setAlreadyTaken(this, survey.getSurveyToken());

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, waitTime);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSsCloseSurveyEvent() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 0);
    }
}