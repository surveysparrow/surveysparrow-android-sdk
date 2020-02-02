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

public final class SsSurveyActivity extends AppCompatActivity implements OnSsResponseEventListener {
    private SsSurvey survey;
    private int activityTheme;
    private CharSequence appbarTitle;
    private boolean enableButton;
    private long waitTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        activityTheme = intent.getIntExtra(SurveySparrow.SS_ACTIVITY_THEME, R.style.SurveyTheme);
        appbarTitle = intent.getStringExtra(SurveySparrow.SS_APPBAR_TITLE);
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

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        SsSurveyFragment surveyFragment = new SsSurveyFragment(survey);
        fragmentTransaction.add(R.id.surveyContainer, surveyFragment);
        fragmentTransaction.commit();
    }

    @Override
    public void onSsResponseEvent(String data) {
        Intent resultIntent = new Intent();
        resultIntent.setData(Uri.parse(data));
        setResult(RESULT_OK, resultIntent);

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
}
