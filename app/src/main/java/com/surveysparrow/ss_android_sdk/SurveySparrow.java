package com.surveysparrow.ss_android_sdk;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.surveysparrow.ss_android_sdk.helpers.SsHelper;
import com.surveysparrow.ss_android_sdk.models.SsActivityConfig;
import com.surveysparrow.ss_android_sdk.models.SsSurvey;
import com.surveysparrow.ss_android_sdk.views.SsSurveyActivity;

public class SurveySparrow {
    private SsSurvey survey;
    private SsActivityConfig activityConfig;
    public static final String SS_SURVEY = "SS_SURVEY";
    public static final String SS_ACTIVITY_CONFIG = "SS_ACTIVITY_CONFIG";

    public SurveySparrow(SsSurvey survey) {
        this.survey = survey;
        this.activityConfig = new SsActivityConfig();
    }

    public SurveySparrow(SsSurvey survey, SsActivityConfig activityConfig) {
        this.survey = survey;
        this.activityConfig = activityConfig;
    }

    public void startSurveyForResult(Activity context, int requestCode) {
        if(!SsHelper.getNetworkState(context)) {
            Toast.makeText(context, "No Network", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(context, SsSurveyActivity.class);
        intent.putExtra(SS_SURVEY, this.survey);
        intent.putExtra(SS_ACTIVITY_CONFIG, this.activityConfig);
        context.startActivityForResult(intent, requestCode);
    }
}
