package com.surveysparrow.ss_android_sdk;


import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.surveysparrow.ss_android_sdk.SsSurvey.CustomParam;
import android.os.AsyncTask;
import java.util.concurrent.ExecutionException;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;
/**
 * Fragment that display the Survey Sparrow survey.
 * Use this fragment to display survey in your activity.
 * To get the survey response implement the OnSsResponseEventListener
 * & override the onSsResponseEvent Method.
 * @see OnSsResponseEventListener
 */
@SuppressLint("SetJavaScriptEnabled")
public final class SsSurveyFragment extends Fragment {
    private SsSurvey survey;
    private String activity;
    private ProgressBar progressBar;
    private ObjectAnimator progressBarAnimator;
    public static final String LOG_TAG = "SS_SAMPLE";
    public static final String SS_VALIDATION = "SS_VALIDATION";
    public JSONObject jsonObject; 
    private OnSsResponseEventListener onSsResponseEventListener;
    private OnSsValidateSurveyEventListener validationListener;

    public void setValidateSurveyListener(OnSsValidateSurveyEventListener listener) {
        validationListener = listener;
    }

    WebView ssWebView;
    /**
     * Create SsSurveyFragment public constructor.
     */
    public SsSurveyFragment() {
        //  public, no-arg constructor 
    }

    /**
     * Set the survey object.
     * 
     * @param survey SsSurvey object.
     * @return Returns the same SsSurveyFragment object, for chaining
     * multiple calls into a single statement.
     */
    public SsSurveyFragment setSurvey(SsSurvey survey) {
        this.survey = survey;
        return this;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        activity = getActivity().getClass().getSimpleName();
        super.onAttach(context);
        onSsResponseEventListener = (OnSsResponseEventListener) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // we are checking the activity name here to avoid duplicate api call                 
        if (!activity.equals("SsSurveyActivity")) {
            CustomParam[] customparam = survey.getCustomParams();
            String apiUrl = "https://"+ survey.getDomain()+"/sdk/validate-survey/"+survey.getSurveyToken();
            APICallTask apiCallTask = new APICallTask(apiUrl,customparam, new APICallTask.ApiCallback() {
            @Override
            public void onResponse(String response) {
                try {
                    jsonObject = new JSONObject(response);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            });
            apiCallTask.execute(apiUrl);
            try {
                apiCallTask.await();  
            } catch (InterruptedException e) {
                Log.e(SS_VALIDATION, "Error in apiCallTask" + e);
            }
            try {
                if(jsonObject.getBoolean("active") != true){
                    if(validationListener != null){
                        validationListener.onSsValidateSurvey(jsonObject);
                        return null;
                    }
                    Log.v(SS_VALIDATION, "survey validation error json" + jsonObject.toString() );
                }
                } catch (Exception e) {
                Log.e(SS_VALIDATION, "Error in  processing  apiCallTask json" + e);
            }    
        }                  

        FrameLayout ssLayout = new FrameLayout(getActivity());

        progressBar = new ProgressBar(getActivity(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6, Gravity.TOP));

        ssWebView = new WebView(getActivity());
        ssWebView.getSettings().setJavaScriptEnabled(true);
        ssWebView.getSettings().setDomStorageEnabled(true);
        ssWebView.addJavascriptInterface(new JsObject(), "SsAndroidSdk");

        ssWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains(SurveySparrow.SS_THANK_YOU_BASE_URL) || survey.getThankYouRedirect() == false) {
                    return super.shouldOverrideUrlLoading(view, url);
                } else {
                    view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
            }
        });

        ssWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                    return;
                }
                progressBarAnimator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), newProgress);
                progressBarAnimator.setDuration(300);
                progressBarAnimator.start();
            }
        });

        ssWebView.loadUrl(this.survey.getSsUrl());

        ssLayout.addView(ssWebView);
        ssLayout.addView(progressBar);
        return ssLayout;
    }


    private class JsObject {
        @JavascriptInterface
        public void shareData(String data) {
            onSsResponseEventListener.onSsResponseEvent(SurveySparrow.toJSON(data));
        }
    }
}
