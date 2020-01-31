package com.surveysparrow.ss_android_sdk;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import com.surveysparrow.ss_android_sdk.helpers.SsHelper;
import com.surveysparrow.ss_android_sdk.models.SsSurvey;
import com.surveysparrow.ss_android_sdk.views.SsSurveyActivity;

public final class SurveySparrow {
    private SsSurvey survey;
    private Activity context;
    private int activityTheme;
    private CharSequence appBarTitle;
    private boolean enableBackButton;
    private long waitTime;

    public static final String SS_SURVEY = "SS_SURVEY";
    public static final String SS_ACTIVITY_THME = "SS_ACTIVITY_THEME";
    public static final String SS_APPBAR_TITLE = "SS_APPBAR_TITLE";
    public static final String SS_BACK_BUTTON = "SS_BACK_BUTTON";
    public static final String SS_WAIT_TIME = "SS_WAIT_TIME";


    private static final String SHARED_PREF_FILE = "com.surveysparrow.android-sdk.SsSurveySharedPref";
    private static final String SHARED_PREF_IS_TAKEN = "IS_ALREADY_TAKEN";
    private static final String SHARED_PREF_NEXT_PROMPT = "LAST_TAKEN";
    private static final int DEFAULT_WAIT_TIME = 3000;

    public SurveySparrow(Activity context, SsSurvey survey) {
        this.survey = survey;
        this.context = context;
        activityTheme = R.style.SurveyTheme;
        enableBackButton = true;
        appBarTitle = context.getString(R.string.ss_activity_title);
        waitTime = DEFAULT_WAIT_TIME;
    }

    public void startSurveyForResult(int requestCode) {
        if (!SsHelper.getNetworkState(context)) {
            Toast.makeText(context, R.string.no_network_message, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(context, SsSurveyActivity.class);
        intent.putExtra(SS_SURVEY, survey);
        intent.putExtra(SS_ACTIVITY_THME, activityTheme);
        intent.putExtra(SS_APPBAR_TITLE, appBarTitle);
        intent.putExtra(SS_BACK_BUTTON, enableBackButton);
        intent.putExtra(SS_WAIT_TIME, waitTime);
        context.startActivityForResult(intent, requestCode);
    }

    public SurveySparrow setActivityTheme(@StyleRes int themeId) {
        activityTheme = themeId;
        return this;
    }

    public SurveySparrow setAppBarTitle(@StringRes int titleId) {
        appBarTitle = context.getString(titleId);
        return this;
    }

    public SurveySparrow setAppBarTitle(CharSequence title) {
        appBarTitle = title;
        return this;
    }

    public SurveySparrow enableBackButton(boolean enable) {
        enableBackButton = enable;
        return this;
    }

    public SurveySparrow setWaitTime(long milliseconds) {
        waitTime = milliseconds;
        return this;
    }

    //    public void scheduleAppRating(int startAfter, int repeatType, int requestCode, String dialogTitle, String dialogMessage, String dialogPositiveButton, String dialogNegativeButton, String dialogNeutralButton) {
//        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_FILE, Context.MODE_PRIVATE);
//        SharedPreferences.Editor sharedPrefEditor = sharedPreferences.edit();
//        boolean isAlreadyTaken = sharedPreferences.getBoolean(SHARED_PREF_IS_TAKEN, false);
//        long nextPrompt = sharedPreferences.getLong(SHARED_PREF_NEXT_PROMPT, -1);
//        long now = new Date().getTime();
//        if(nextPrompt == -1) {
//            sharedPrefEditor.putLong(SHARED_PREF_NEXT_PROMPT, nextPrompt);
//        } else if(nextPrompt > now) {
//
//        }
//        sharedPrefEditor.apply();
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(context);
//    }
}
