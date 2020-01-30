package com.surveysparrow.ss_android_sdk.models;

import java.io.Serializable;

public final class SsSurvey implements Serializable {
    private String ssUrl;

    public SsSurvey(String ssDomain, String ssSurveyToken) {
        this.ssUrl = "https://" + ssDomain + "/s/android/" + ssSurveyToken;
    }

    public String getSsUrl() {
        return ssUrl;
    }
}
