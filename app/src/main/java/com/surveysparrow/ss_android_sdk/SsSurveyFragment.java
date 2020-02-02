package com.surveysparrow.ss_android_sdk;


import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

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
    private ProgressBar progressBar;
    private ObjectAnimator progressBarAnimator;

    private OnSsResponseEventListener onSsResponseEventListener;

    /**
     * Create SsSurveyFragment.
     * @param survey SsSurvey object.
     * @see SsSurvey
     */
    public SsSurveyFragment(SsSurvey survey) {
        this.survey = survey;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        onSsResponseEventListener = (OnSsResponseEventListener) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FrameLayout ssLayout = new FrameLayout(getActivity());

        progressBar = new ProgressBar(getActivity(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6, Gravity.TOP));

        WebView ssWebView = new WebView(getActivity());
        ssWebView.getSettings().setJavaScriptEnabled(true);
        ssWebView.getSettings().setDomStorageEnabled(true);
        ssWebView.addJavascriptInterface(new JsObject(), "SsAndroidSdk");

        ssWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains(SurveySparrow.SS_THANK_YOU_BASE_URL)) {
                    return super.shouldOverrideUrlLoading(view, url);
                }
                view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
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
//        ssWebView.loadUrl("https://react-7hjmqi.stackblitz.io");

        ssLayout.addView(ssWebView);
        ssLayout.addView(progressBar);
        return ssLayout;
    }


    private class JsObject {
        @JavascriptInterface
        public void shareData(String data) {
            onSsResponseEventListener.onSsResponseEvent(data);
        }
    }
}
