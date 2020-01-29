package com.surveysparrow.ss_android_sdk.views;


import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

/**
 * A simple {@link Fragment} subclass.
 */
@SuppressLint("SetJavaScriptEnabled")
public class SsSurveyFragment extends Fragment {
    private String surveyUrl;

    public SsSurveyFragment(String surveyUrl) {
        this.surveyUrl = surveyUrl;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        WebView ssWebView = new WebView(getActivity());
        ssWebView.getSettings().setJavaScriptEnabled(true);
        ssWebView.getSettings().setDomStorageEnabled(true);
        ssWebView.loadUrl(this.surveyUrl);
        return ssWebView;
    }
}
