package com.surveysparrow.ss_android_sdk.models;

import java.io.Serializable;

public final class SsSurvey implements Serializable {
    private String baseUrl;
    private String customVariableString = "?";

    public static class CustomParam {
        private CharSequence name;
        private CharSequence value;

        public CustomParam(CharSequence name, CharSequence value) {
            this.name = name;
            this.value = value;
        }
    }

    public SsSurvey(CharSequence domain, CharSequence surveyToken) {
        this.baseUrl = generateBaseUrl(domain, surveyToken);
    }

    public SsSurvey(CharSequence domain, CharSequence surveyToken, CustomParam[] customParams) {
        this.baseUrl = generateBaseUrl(domain, surveyToken);
        addCustomParams(customParams);
    }

    public String getSsUrl() {
        return baseUrl + customVariableString;
    }

    public SsSurvey addCustomParam(CustomParam customParam) {
        addCustomParam(customParam.name, customParam.value);
        return this;
    }

    public  SsSurvey addCustomParam(CharSequence name, CharSequence value) {
        customVariableString += name + "=" + value + "&";
        return this;
    }

    public SsSurvey addCustomParams(CustomParam[] customParams) {
        for (CustomParam customParam : customParams) {
            addCustomParam(customParam);
        }
        return this;
    }

    private String generateBaseUrl(CharSequence domain, CharSequence surveyToken) {
        return "https://" + domain + "/s/android/" + surveyToken;
    }
}
