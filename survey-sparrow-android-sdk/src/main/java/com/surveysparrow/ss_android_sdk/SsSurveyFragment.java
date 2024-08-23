package com.surveysparrow.ss_android_sdk;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;

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

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
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
    private int widgetContactId = 0;
    private ProgressBar progressBar;
    private ObjectAnimator progressBarAnimator;
    public static final String LOG_TAG = "SS_SAMPLE";
    public static final String SS_VALIDATION = "SS_VALIDATION";
    public JSONObject jsonObject;
    public JSONObject closeSurveyJSONObject;
    private OnSsResponseEventListener onSsResponseEventListener;
    public Boolean surveyCompleted = false;
    private OnSsValidateSurveyEventListener validationListener;
    private OnSsCloseSurveyEventListener closeSurveyListener;

    public void setValidateSurveyListener(OnSsValidateSurveyEventListener listener) {
        validationListener = listener;
    }

    public void setCloseSurveyListener(OnSsCloseSurveyEventListener listener) {
        closeSurveyListener = listener;
    }

    public void setData(int id) {
        widgetContactId = id;
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
            String apiUrl = "https://" + survey.getDomain() + "/sdk/validate-survey/" + survey.getSurveyToken();
            // Create a CompletableFuture for the asynchronous API call
            CompletableFuture<String> apiCallFuture = new CompletableFuture<>();

            APICallTask apiCallTask = new APICallTask(apiUrl, customparam, new APICallTask.ApiCallback() {
                @Override
                public void onResponse(String response) {
                    apiCallFuture.complete(response);
                }
            });

            // Execute the AsyncTask asynchronously
            apiCallTask.execute(apiUrl);

            // Wait for the asynchronous API call to complete
            apiCallFuture.join();

            try {
                String response = apiCallFuture.get(); // Get the result of the API call
                JSONObject jsonObject = new JSONObject(response);

                if (validationListener != null) {
                    validationListener.onSsValidateSurvey(jsonObject);
                }
                Log.v(SS_VALIDATION, "survey validation json" + jsonObject.toString());
                if (jsonObject.getBoolean("active") != true) {
                        return null;
                }
                if (jsonObject.has("widgetContactId")) {
                    widgetContactId = jsonObject.getInt("widgetContactId");
                }
            } catch (Exception e) {
                Log.e(SS_VALIDATION, "Error in  processing  apiCallTask json" + e);
            }
        }

        final FrameLayout ssLayout = new FrameLayout(getActivity());

        progressBar = new ProgressBar(getActivity(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6, Gravity.TOP));

        ssWebView = new WebView(getActivity());
        ssWebView.getSettings().setJavaScriptEnabled(true);
        ssWebView.getSettings().setDomStorageEnabled(true);
        ssWebView.addJavascriptInterface(new JsObject(), "SsAndroidSdk");

        final ImageButton closeButton = new ImageButton(getActivity());
        closeButton.setImageResource(R.drawable.ic_close_black);
        closeButton.setBackgroundResource(R.drawable.ic_close_rounded);
        closeButton.setClickable(true);
        closeButton.setPadding(10, 10, 10, 10);
        closeButton.setVisibility(View.GONE);

        FrameLayout.LayoutParams closeButtonParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        closeButtonParams.setMargins(0, 60, 60, 0);
        closeButtonParams.gravity = Gravity.TOP | Gravity.END;
        closeButton.setLayoutParams(closeButtonParams);
        HashMap properties = survey.getProperties();

        // Set an OnClickListener for the close button
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String closeSurveyUrl = "https://" + survey.getDomain() + "/nps/widget/contact/" + widgetContactId;
                CloseSurvey closeSurvey = new CloseSurvey(closeSurveyUrl, survey.getSurveyToken(), widgetContactId, surveyCompleted, new CloseSurvey.CloseSurveyCallback() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            closeSurveyJSONObject = new JSONObject(response);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
                closeSurvey.execute(closeSurveyUrl);
                try {
                    closeSurvey.await();
                } catch (InterruptedException e) {
                    Log.e(SS_VALIDATION, "Error in closeSurvey" + e);
                }

                try {
                    if (closeSurveyJSONObject.getBoolean("surveyClosed") == true) {
                        if (closeSurveyListener != null) {
                            closeSurveyListener.onSsCloseSurveyEvent();
                        } else {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ssLayout.setVisibility(View.GONE);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(SS_VALIDATION, "Error in  processing  close survey json" + e);
                }
            }
        });
        ssWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains(SurveySparrow.SS_THANK_YOU_BASE_URL) || !survey.getThankYouRedirect()) {
                    return super.shouldOverrideUrlLoading(view, url);
                } else {
                    view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if ((!properties.containsKey("isButtonEnabled")) || Boolean.TRUE.equals(properties.get("isButtonEnabled"))) {
                    String jsCode = "var observer = new MutationObserver(function(mutations) { mutations.forEach(function(mutation) { var elements = document.getElementsByClassName('ss-language-selector--wrapper ss-survey-font-family'); if (elements.length > 0) { for (var i = 0; i < elements.length; i++) { elements[i].style.marginRight = '45px'; } observer.disconnect(); } }); }); observer.observe(document.body, { childList: true, subtree: true });";
                    view.evaluateJavascript(jsCode, null);
                }
            }

        });

        ssWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                    closeButton.setVisibility(View.VISIBLE);
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
        if( (!properties.containsKey("isButtonEnabled")) || Boolean.TRUE.equals(properties.get("isButtonEnabled") )) {
            ssLayout.addView(closeButton);
        }
        return ssLayout;
    }

    private class JsObject {
        @JavascriptInterface
        public void shareData(String data) {
            surveyCompleted = true;
            onSsResponseEventListener.onSsResponseEvent(SurveySparrow.toJSON(data));
        }
    }
}