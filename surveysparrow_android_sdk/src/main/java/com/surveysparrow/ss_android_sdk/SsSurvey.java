package com.surveysparrow.ss_android_sdk;
import android.net.Uri;
import androidx.annotation.IntDef;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.net.URLEncoder;

public final class SsSurvey implements Serializable {
    private String baseUrl;
    private String surveyToken;
    private String surveyDomain;
    private HashMap properties ;
    public transient CustomParam[] customParamsValue;
    private String customVariableString = "?";
    private int surveyType = SurveySparrow.CLASSIC;
    private boolean isThankYouRedirect = true;

    /**
     * Custom param class
     */
    public static class CustomParam {
        public CharSequence name;
        public CharSequence value;

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
     * @param domain      Domain name.
     * @param surveyToken Survey token.
     */
    public SsSurvey(CharSequence domain, CharSequence surveyToken) {
        this.baseUrl = generateBaseUrl(domain, surveyToken);
        this.surveyToken = surveyToken.toString();
        this.surveyDomain = domain.toString();
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
        this.surveyToken = surveyToken.toString();
        this.surveyDomain = domain.toString();
        this.customParamsValue = customParams;
        addCustomParams(customParams);
    }

    public SsSurvey(CharSequence domain, CharSequence surveyToken, CustomParam[] customParams, HashMap properties) {
        this.baseUrl = generateBaseUrl(domain, surveyToken);
        this.surveyToken = surveyToken.toString();
        this.surveyDomain = domain.toString();
        this.customParamsValue = customParams;
        this.properties = properties ;
        addCustomParams(customParams);
        addCustomParam("sparrowLang", properties.get("langCode").toString());
    }

    String getSsUrl() {
        return baseUrl + customVariableString;
    }

    String getSurveyToken() {
        return surveyToken;
    }

    String getDomain() {
        return surveyDomain;
    }

    HashMap getProperties() {
        return properties;
    }

    CustomParam[] getCustomParams() {
        return customParamsValue;
    }

    boolean getThankYouRedirect() {
        return isThankYouRedirect;
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
        try {
            String encodedValue = URLEncoder.encode(value.toString(), "UTF-8");
            customVariableString += name + "=" + encodedValue + "&";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(); // Or handle as needed
        }
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

    /**
     * Whether to redirect to thank you page outside the webview
     *
     * @param thankYouRedirect Boolean to toggle the redirect behaviour
     * @return Returns the same SsSurvey object, for chaining
     * multiple calls into a single statement.
     */
    public SsSurvey setThankYouRedirect(boolean thankYouRedirect) {
        isThankYouRedirect = thankYouRedirect;
        return this;
    }
}
