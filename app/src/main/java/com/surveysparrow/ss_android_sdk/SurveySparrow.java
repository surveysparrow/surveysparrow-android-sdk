package com.surveysparrow.ss_android_sdk;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.surveysparrow.ss_android_sdk.helpers.SsHelper;
import com.surveysparrow.ss_android_sdk.models.SsSurvey;
import com.surveysparrow.ss_android_sdk.views.SsSurveyActivity;

public class SurveySparrow {
    private SsSurvey survey;
    public static final String SS_SURVEY = "SS_SURVEY";

    SurveySparrow(SsSurvey survey) {
        this.survey = survey;
    }

    public void startSurveyForResult(Activity context, int requestCode) {
        if(!SsHelper.getNetworkState(context)) {
            Toast.makeText(context, "No Network", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(context, SsSurveyActivity.class);
        intent.putExtra(SS_SURVEY, this.survey);
        context.startActivityForResult(intent, requestCode);
    }
}
