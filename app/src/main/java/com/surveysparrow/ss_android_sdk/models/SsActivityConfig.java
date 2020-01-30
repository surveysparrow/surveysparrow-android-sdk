package com.surveysparrow.ss_android_sdk.models;

import com.surveysparrow.ss_android_sdk.R;

import java.io.Serializable;

public class SsActivityConfig implements Serializable {
    private int activityTheme = R.style.SurveyTheme;
    private String appBarTitle = "Feedback";
    private boolean enableBackButton = false;

    public SsActivityConfig() {
    }

    public SsActivityConfig(int activityTheme, String appBarTitle, boolean enableBackButton) {
        this.activityTheme = activityTheme;
        this.appBarTitle = appBarTitle;
        this.enableBackButton = enableBackButton;
    }

    public int getActivityTheme() {
        return activityTheme;
    }

    public void setActivityTheme(int activityTheme) {
        this.activityTheme = activityTheme;
    }

    public String getAppBarTitle() {
        return appBarTitle;
    }

    public void setAppBarTitle(String appBarTitle) {
        this.appBarTitle = appBarTitle;
    }

    public boolean isEnableBackButton() {
        return enableBackButton;
    }

    public void enableBackButton(boolean enableBackButton) {
        this.enableBackButton = enableBackButton;
    }
}
