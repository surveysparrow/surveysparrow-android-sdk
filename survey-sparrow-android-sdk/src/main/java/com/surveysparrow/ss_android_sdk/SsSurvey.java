package com.surveysparrow.ss_android_sdk;

import android.net.Uri;

import androidx.annotation.IntDef;

import java.io.Serializable;

public final class SsSurvey implements Serializable {
    private String baseUrl;
    private String surveyToken;
    private String customVariableString = "?";
    private int surveyType = SurveySparrow.CLASSIC;

    /**
     * Custom param class
     */
    public static class CustomParam {
        private CharSequence name;
        private CharSequence value;

        /**
         * Create custom param.
         *
         * @param name  Custom param name.
         * @param value Custom param value.
         */
        public CustomParam(CharSequence name, CharSequence value) {
            this.name = name;
            this.value = value;
        }
    }

    /**
     * Create SsSurvey.
     *
     * @param domain      Domain name.
     * @param surveyToken Survey token.
     */
    public SsSurvey(CharSequence domain, CharSequence surveyToken) {
        this.baseUrl = generateBaseUrl(domain, surveyToken);
        this.surveyToken = surveyToken.toString();
    }

    /**
     * Create SsSurvey with custom params
     *
     * @param domain       Domain name.
     * @param surveyToken  Survey Token.
     * @param customParams CustomParams Array.
     * @see CustomParam
     */
    public SsSurvey(CharSequence domain, CharSequence surveyToken, CustomParam[] customParams) {
        this.baseUrl = generateBaseUrl(domain, surveyToken);
        addCustomParams(customParams);
    }

    String getSsUrl() {
        return baseUrl + customVariableString;
    }

    String getSurveyToken() {
        return surveyToken;
    }

    /**
     * Add custom param to survey.
     *
     * @param customParam CustomParam object
     * @return Returns the same SsSurvey object, for chaining
     * multiple calls into a single statement.
     * @see CustomParam
     */
    public SsSurvey addCustomParam(CustomParam customParam) {
        addCustomParam(customParam.name, customParam.value);
        return this;
    }

    /**
     * Add custom params by name & value
     *
     * @param name  Custom param name
     * @param value Custom param value
     * @return Returns the same SsSurvey object, for chaining
     * multiple calls into a single statement.
     */
    public SsSurvey addCustomParam(CharSequence name, CharSequence value) {
        customVariableString += name + "=" + value + "&";
        return this;
    }

    /**
     * Add multiple custom params at once.
     *
     * @param customParams CustomParam object array
     * @return Returns the same SsSurvey object, for chaining
     * multiple calls into a single statement.
     * @see CustomParam
     */
    public SsSurvey addCustomParams(CustomParam[] customParams) {
        for (CustomParam customParam : customParams) {
            addCustomParam(customParam);
        }
        return this;
    }

    @IntDef({SurveySparrow.CLASSIC, SurveySparrow.CHAT, SurveySparrow.NPS})
    private @interface SurveyType {
    }

    /**
     * Set type of the survey.
     *
     * @param surveyType Survey Type
     * @return Returns the same SsSurvey object, for chaining
     * multiple calls into a single statement.
     */
    public SsSurvey setSurveyType(@SurveyType int surveyType) {
        this.surveyType = surveyType;
        return this;
    }

    private String generateBaseUrl(CharSequence domain, CharSequence surveyToken) {
        return "https://" + domain + "/" + (surveyType == SurveySparrow.NPS ? 'n' : 's') + "/android/" + surveyToken;
    }
}
