package com.surveysparrow.ss_android_sdk.models;

public class SsSurvey {
    private String ssDomain;
    private String ssSurveyToken;
    private String ssUrl;

    public SsSurvey(String ssDomain, String ssSurveyToken) {
        this.ssDomain = ssDomain;
        this.ssSurveyToken = ssSurveyToken;
        this.ssUrl = "https://" + this.ssDomain + "/s/android/" + this.ssSurveyToken;
    }

    public String getSsDomain() {
        return ssDomain;
    }

    public String getSsSurveyToken() {
        return ssSurveyToken;
    }

    public String getSsUrl() {
        return ssUrl;
    }
}
