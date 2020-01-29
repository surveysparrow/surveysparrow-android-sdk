package com.surveysparrow.ss_android_sdk.views;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;

import com.surveysparrow.ss_android_sdk.R;
import com.surveysparrow.ss_android_sdk.SurveySparrow;

public class SsSurveyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ss_survey);

        String surveyUrl = getIntent().getStringExtra(SurveySparrow.SURVEY_URL);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        SsSurveyFragment surveyFragment = new SsSurveyFragment(surveyUrl);
        fragmentTransaction.add(R.id.surveyContainer, surveyFragment);
        fragmentTransaction.commit();
    }
}
