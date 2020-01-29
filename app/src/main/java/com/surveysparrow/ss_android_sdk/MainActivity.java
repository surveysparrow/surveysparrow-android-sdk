package com.surveysparrow.ss_android_sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import com.surveysparrow.ss_android_sdk.models.SsSurvey;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void startSurvey(View v) {
        SsSurvey survey = new SsSurvey("some-company.surveysparrow.com", "tt-b6a21f");
        SurveySparrow ss = new SurveySparrow(survey);
        ss.startSurvey(this);
    }
}
