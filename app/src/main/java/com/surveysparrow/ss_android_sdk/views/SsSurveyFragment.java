package com.surveysparrow.ss_android_sdk.views;


import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.surveysparrow.ss_android_sdk.helpers.OnResponseEventListener;

/**
 * A simple {@link Fragment} subclass.
 */
@SuppressLint("SetJavaScriptEnabled")
public class SsSurveyFragment extends Fragment {
    private String surveyUrl;

    private OnResponseEventListener onResponseEventListener;

    public SsSurveyFragment(String surveyUrl) {
        this.surveyUrl = surveyUrl;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        onResponseEventListener = (OnResponseEventListener) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        WebView ssWebView = new WebView(getActivity());
        ssWebView.getSettings().setJavaScriptEnabled(true);
        ssWebView.getSettings().setDomStorageEnabled(true);
        ssWebView.addJavascriptInterface(new JsObject(getActivity()), "SsAndroidSdk");
        ssWebView.loadUrl(this.surveyUrl);
//        ssWebView.loadUrl("https://react-7hjmqi.stackblitz.io");
        return ssWebView;
    }



    private class JsObject {
        private Context context;

        JsObject(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void sendToJava(String data) {
            Log.v("EEE", "Good");
            onResponseEventListener.responseEvent(data);
        }
    }
}
