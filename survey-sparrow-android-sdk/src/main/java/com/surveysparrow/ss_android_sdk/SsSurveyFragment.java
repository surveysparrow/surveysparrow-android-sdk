package com.surveysparrow.ss_android_sdk;
import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.webkit.ValueCallback;
import android.widget.ImageButton;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
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
import android.widget.Toast;
import com.surveysparrow.ss_android_sdk.SsSurvey.CustomParam;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
    public static final String SS_VALIDATION = "SS_VALIDATION";
    public JSONObject closeSurveyJSONObject;
    private OnSsResponseEventListener onSsResponseEventListener;
    public Boolean surveyCompleted = false;
    private OnSsValidateSurveyEventListener validationListener;
    private OnSsCloseSurveyEventListener closeSurveyListener;
    private boolean isCaptureImageActive = false;
    private ActivityResultLauncher<Intent> imageCaptureLauncher;

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArray;
    private static final int REQUEST_IMAGE_CAPTURE = 1185;
    private static final int FILE_CHOOSER_RESULT_CODE = 1183;
    private static final int REQUEST_SELECT_FILE = 1184;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private Uri capturedImageUri = null;
    private Uri mCameraImageUri;
    private WebView ssWebView;
    private ActivityResultLauncher<String> permissionLauncher;


    public void setValidateSurveyListener(OnSsValidateSurveyEventListener listener) {
        validationListener = listener;
    }

    public void setCloseSurveyListener(OnSsCloseSurveyEventListener listener) {
        closeSurveyListener = listener;
    }

    public void setData(int id) {
        widgetContactId = id;
    }

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

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                            imageCaptureLauncher.launch(intent);
                        }
                        
                        else{
                            isCaptureImageActive = false;
                            Log.d("Error","Error in launching the the camera");
                        }
                    } else {
                        isCaptureImageActive= false;
                        Toast.makeText(getActivity(), "Camera permission is required", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        imageCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleImageCaptureResult(result.getResultCode(), result.getData())
        );
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
                if (!jsonObject.getBoolean("active")) {
                    return null;
                }
                if (jsonObject.has("widgetContactId")) {
                    widgetContactId = jsonObject.getInt("widgetContactId");
                }
            } catch (Exception e) {
                Log.e(SS_VALIDATION, "Error in  processing  apiCallTask json" + e);
            }
        }

        final FrameLayout ssLayout = new FrameLayout(requireActivity());

        progressBar = new ProgressBar(getActivity(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6, Gravity.TOP));
        ssWebView = new WebView(getActivity());
        ssWebView.getSettings().setJavaScriptEnabled(true);
        ssWebView.getSettings().setDomStorageEnabled(true);
        ssWebView.addJavascriptInterface(new JsObject(), "SsAndroidSdk");

        ConstraintLayout constraintLayout = new ConstraintLayout(getActivity());
        constraintLayout.setLayoutParams(new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
        ));

        final ImageButton closeButton = new ImageButton(getActivity());
        closeButton.setImageResource(R.drawable.ic_close_black);
        closeButton.setBackgroundResource(R.drawable.ic_close_rounded);
        closeButton.setClickable(true);
        closeButton.setPadding(10, 10, 10, 10);
        closeButton.setVisibility(View.GONE);

        ConstraintLayout.LayoutParams closeButtonParams = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );

        int marginInDp = 20;
        int marginInPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, marginInDp, getResources().getDisplayMetrics()
        );

        closeButtonParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        closeButtonParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        closeButtonParams.setMargins(0, marginInPx, marginInPx, 0);
        closeButton.setLayoutParams(closeButtonParams);
        constraintLayout.addView(closeButton);
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
                    if (closeSurveyJSONObject.getBoolean("surveyClosed")) {
                        if (closeSurveyListener != null) {
                            closeSurveyListener.onSsCloseSurveyEvent();
                        } else {
                            requireActivity().runOnUiThread(new Runnable() {
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
                if ((!properties.containsKey("isCloseButtonEnabled")) || Boolean.TRUE.equals(properties.get("isCloseButtonEnabled"))) {
                    String jsCode = "const styleTag = document.createElement(\"style\"); styleTag.innerHTML = `.ss-language-selector--wrapper { margin-right: 45px; }`; document.body.appendChild(styleTag);";
                    view.evaluateJavascript(jsCode, null);
                }
            }

        });

        ssWebView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {

                if (mUploadMessageArray != null) {
                    mUploadMessageArray.onReceiveValue(null);
                }
                mUploadMessageArray = filePathCallback;
                if(isCaptureImageActive){
                    return true;
                }

                else {

                    Intent intent = fileChooserParams.createIntent();
                    try {
                        startActivityForResult(intent, REQUEST_SELECT_FILE);
                    } catch (ActivityNotFoundException e) {
                        mUploadMessageArray = null;
                        Toast.makeText(requireActivity().getApplicationContext(), "Cannot open file chooser", Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
                return true;
            }

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
        if( (!properties.containsKey("isCloseButtonEnabled")) || Boolean.TRUE.equals(properties.get("isCloseButtonEnabled") )) {
            ssLayout.addView(constraintLayout);
        }
        return ssLayout;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SELECT_FILE) {
            if (mUploadMessageArray == null) return;
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            mUploadMessageArray.onReceiveValue(results);
            mUploadMessageArray = null;
        } else if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (mUploadMessage == null) return;
            Uri result = data == null || resultCode != Activity.RESULT_OK ? null : data.getData();
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    private void handleImageCaptureResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
            if (imageBitmap != null) {
                File file = new File(requireContext().getFilesDir(), "image_" + System.currentTimeMillis() + ".png");
                capturedImageUri = Uri.fromFile(file);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    mUploadMessageArray.onReceiveValue(new Uri[]{capturedImageUri});
                } catch (IOException e) {
                    mUploadMessageArray.onReceiveValue(null);
                }
                mUploadMessageArray = null;
            }
        }

        if(resultCode != Activity.RESULT_OK){
            mUploadMessageArray.onReceiveValue(null);
            mUploadMessageArray = null;
        }

        isCaptureImageActive = false;
    }

    private class JsObject {
        @JavascriptInterface
        public void shareData(String data) {
            surveyCompleted = true;
            onSsResponseEventListener.onSsResponseEvent(SurveySparrow.toJSON(data));
        }

        @JavascriptInterface
        public void captureImage() {
            if (!isCaptureImageActive) {
                isCaptureImageActive = true;

                if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {

                    permissionLauncher.launch(Manifest.permission.CAMERA);

                } else {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    if (intent.resolveActivity(getActivity().getPackageManager()) != null) {

                        imageCaptureLauncher.launch(intent);

                    }
                    else{
                        isCaptureImageActive = false;
                        Log.d("Error","Error in launching the the camera");
                    }
                }

            }

        }
    }
}