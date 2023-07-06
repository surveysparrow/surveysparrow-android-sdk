package com.surveysparrow.ss_android_sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;
import android.os.AsyncTask;

import androidx.annotation.IntDef;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import com.surveysparrow.ss_android_sdk.SsSurvey.CustomParam;

/**
 * Survey Sparrow helper class.
 */
public final class SurveySparrow {
    private SsSurvey survey;
    public static final String LOG_TAG = "SS_SAMPLE";
    private Activity context;
    public JSONObject jsonObject; 

    private int activityTheme = R.style.SurveyTheme;
    private CharSequence appBarTitle;
    private boolean enableBackButton = true;
    private long waitTime = SS_DEFAULT_WAIT_TIME;

    private CharSequence dialogTitle;
    private CharSequence dialogMessage;
    private CharSequence dialogPositiveButtonText;
    private CharSequence dialogNegativeButtonText;

    private String schedulePrefFileName;
    private long startAfter = TimeUnit.DAYS.toMillis(5);
    private long repeatInterval = TimeUnit.DAYS.toMillis(10);
    private int repeatType = REPEAT_TYPE_INCREMENTAL;
    private int feedbackType = SINGLE_FEEDBACK;

    private static boolean debugMode = false;

    private boolean _isAlreadyTaken;
    private long _promptTime;
    private int _incrementMultiplier;

    /**
     * Repeat survey with a constant interval
     */
    public static final int REPEAT_TYPE_CONSTANT = 1;

    /**
     * Repeat survey with an incremental interval
     */
    public static final int REPEAT_TYPE_INCREMENTAL = 2;

    /**
     * Collect schedule feedback for once.
     */
    public static final int SINGLE_FEEDBACK = 1;

    /**
     * Collect scheduled feedback multiple times.
     * (Make sure that you have enabled 'Allow multiple submissions per user' for this survey)
     */
    public static final int MULTIPLE_FEEDBACK = 2;

    /**
     * Survey Types
     */
    public static final int CLASSIC = 1;
    public static final int CHAT = 2;
    public static final int NPS = 3;

    /**
     * Debug log tag.
     */
    public static final String SS_DEBUG_LOG = "SS_DEBUG_LOG";
    public static final String SS_VALIDATION = "SS_VALIDATION";

    static final String SS_SURVEY = "SS_SURVEY";
    static final String SS_ACTIVITY_THEME = "SS_ACTIVITY_THEME";
    static final String SS_APPBAR_TITLE = "SS_APPBAR_TITLE";
    static final String SS_BACK_BUTTON = "SS_BACK_BUTTON";
    static final String SS_WAIT_TIME = "SS_WAIT_TIME";
    static final long SS_DEFAULT_WAIT_TIME = 3000;
    static final String SS_THANK_YOU_BASE_URL = "https://surveysparrow.com/thankyou";

    private static final String SHARED_PREF_FILE = "com.surveysparrow.android-sdk.SsSurveySharedPref";
    private static final String SHARED_PREF_IS_TAKEN = "IS_ALREADY_TAKEN";
    private static final String SHARED_PREF_PROMPT_TIME = "PROMPT_TIME";
    private static final String SHARED_PREF_INCREMENT = "INCREMENT_MULTIPLIER";
    private OnSsValidateSurveyEventListener validationListener;

    public void setValidateSurveyListener(OnSsValidateSurveyEventListener listener) {
        validationListener = listener;
    }

    /**
     * Build a SurveySparrow object. By using this object you will be able to start a
     * SsSurveyActivity and obtain the survey response in the onActivityResult()
     * in the calling activity.
     *
     * @param context The Activity that start the SsSurveyActivity and handle the response
     *                on the onActivityResult method.
     * @param survey  The SsSurvey object that contain the Survey Sparrow account domain and token.
     * @see SsSurvey
     */
    public SurveySparrow(Activity context, SsSurvey survey) {
        this.survey = survey;
        this.context = context;

        appBarTitle = context.getString(R.string.ss_activity_title);

        schedulePrefFileName = SHARED_PREF_FILE + "." + survey.getSurveyToken();

        dialogTitle = context.getString(R.string.dialog_title);
        dialogMessage = context.getString(R.string.dialog_message);
        dialogPositiveButtonText = context.getString(R.string.dialog_positive_button);
        dialogNegativeButtonText = context.getString(R.string.dialog_negative_button);
    }

    /**
     * Start a SsSurveyActivity for result.
     * Use onActivityResult on the calling Activity to handle the survey response.
     *
     * @param requestCode You can use this requestCode in the onActivityResult of the
     *                    calling Activity to handle the response.
     */
    public void startSurveyForResult(int requestCode) {
        if (!isNetworkConnected()) {
            Toast.makeText(context, R.string.no_network_message, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(context, SsSurveyActivity.class);
        intent.putExtra(SS_SURVEY, survey);
        intent.putExtra(SS_ACTIVITY_THEME, activityTheme);
        intent.putExtra(SS_APPBAR_TITLE, appBarTitle);
        intent.putExtra(SS_BACK_BUTTON, enableBackButton);
        intent.putExtra(SS_WAIT_TIME, waitTime);
        context.startActivityForResult(intent, requestCode);
    }


    public void startSurvey(int requestCode) {
        if (!isNetworkConnected()) {
            Toast.makeText(context, R.string.no_network_message, Toast.LENGTH_SHORT).show();
            return;
        }
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
                }
                Log.v(SS_VALIDATION, "survey validation error json" + jsonObject.toString() );
                return;
            }
         } catch (Exception e) {
                Log.e(SS_VALIDATION, "Error in  processing  apiCallTask json" + e);
        } 
         startSurveyForResult(requestCode);
    }

    /**
     * Enable debug mode to view useful log messages while development.
     * Only use this while development.
     *
     * @param enable set this to true to enable debug mode.
     */
    public static void enableDebugMode(boolean enable) {
        debugMode = enable;
    }

    /**
     * Convert String to JSONObject
     *
     * @param text JSON String
     * @return Returns the JSONObject
     */
    public static JSONObject toJSON(String text) {
        JSONObject json;
        try {
            json = new JSONObject(text);
        } catch (JSONException e) {
            if (debugMode)
                Log.e(SS_DEBUG_LOG, e.getStackTrace().toString());
            return null;
        }
        return json;
    }

    /**
     * Set survey activity theme.
     *
     * @param themeId Resource ID of theme style to set
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setActivityTheme(@StyleRes int themeId) {
        activityTheme = themeId;
        return this;
    }

    /**
     * Set survey Activity action bar title.
     *
     * @param titleId Resource ID of title string to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setAppBarTitle(@StringRes int titleId) {
        appBarTitle = context.getString(titleId);
        return this;
    }

    /**
     * Set survey Activity action bar title.
     *
     * @param title Title to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setAppBarTitle(CharSequence title) {
        appBarTitle = title;
        return this;
    }

    /**
     * Enable back button on the action bar of the survey activity.
     *
     * @param enable Set true to display back button, false to hide it.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow enableBackButton(boolean enable) {
        enableBackButton = enable;
        return this;
    }

    /**
     * How long the activity should display the thank you page.
     *
     * @param milliseconds Time to display the thank you page.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setWaitTime(long milliseconds) {
        waitTime = milliseconds;
        return this;
    }

    /**
     * Set schedule alert dialog title.
     *
     * @param title Title to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setDialogTitle(CharSequence title) {
        dialogTitle = title;
        return this;
    }

    /**
     * Set schedule alert dialog title.
     *
     * @param titleId Resource ID of title string to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setDialogTitle(@StringRes int titleId) {
        dialogTitle = context.getString(titleId);
        return this;
    }

    /**
     * Set schedule alert dialog message.
     *
     * @param message Message to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setDialogMessage(CharSequence message) {
        dialogMessage = message;
        return this;
    }

    /**
     * Set schedule alert dialog message.
     *
     * @param messageId Resource ID of message string to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setDialogMessage(@StringRes int messageId) {
        dialogMessage = context.getString(messageId);
        return this;
    }

    /**
     * Set schedule alert dialog positive button text.
     *
     * @param text Button text to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setDialogPositiveButtonText(CharSequence text) {
        dialogPositiveButtonText = text;
        return this;
    }

    /**
     * Set schedule alert dialog positive button text.
     *
     * @param textId Resource ID of button text string to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setDialogPositiveButtonText(@StringRes int textId) {
        dialogPositiveButtonText = context.getString(textId);
        return this;
    }

    /**
     * Set schedule alert dialog negative button text.
     *
     * @param text Button text to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setDialogNegativeButtonText(CharSequence text) {
        dialogNegativeButtonText = text;
        return this;
    }

    /**
     * Set schedule alert dialog negative button text.
     *
     * @param textId Resource ID of button text string to set.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setDialogNegativeButtonText(@StringRes int textId) {
        dialogNegativeButtonText = context.getString(textId);
        return this;
    }

    /**
     * Set the amount of time to wait before showing the scheduled dialog
     * after launching the app for first time.
     *
     * @param milliseconds Start after in milliseconds.
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setStartAfter(long milliseconds) {
        this.startAfter = milliseconds;
        return this;
    }

    /**
     * Set the amount of time to wait to show the dialog once the user declined
     * the dialog or accepted in the case of multiple feedback enabled.
     *
     * @param milliseconds Repeat interval in milliseconds
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setRepeatInterval(long milliseconds) {
        this.repeatInterval = milliseconds;
        return this;
    }

    @IntDef({REPEAT_TYPE_CONSTANT, REPEAT_TYPE_INCREMENTAL})
    private @interface RepeatType {
    }

    /**
     * Set schedule repeat type.
     *
     * @param repeatType One of REPEAT_TYPE_CONSTANT or REPEAT_TYPE_INCREMENTAL
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setRepeatType(@RepeatType int repeatType) {
        this.repeatType = repeatType;
        return this;
    }

    @IntDef({SINGLE_FEEDBACK, MULTIPLE_FEEDBACK})
    private @interface FeedbackType {
    }

    /**
     * Set schedule feedback type.
     *
     * @param feedbackType One of SINGLE_FEEDBACK or MULTIPLE_FEEDBACK
     * @return Returns the same SurveySparrow object, for chaining
     * multiple calls into a single statement.
     */
    public SurveySparrow setFeedbackType(@FeedbackType int feedbackType) {
        this.feedbackType = feedbackType;
        return this;
    }

    /**
     * Schedule a take survey dialog to show after opening the activity for first time,
     * it will show again after the repeat interval if denied, even if the user accept
     * to take the survey you can show the dialog again by setting feedback type
     * to MULTIPLE_FEEDBACK.
     *
     * @param requestCode You can use this requestCode in the onActivityResult of the
     *                    calling Activity to handle the response.
     */
    public void scheduleSurvey(final int requestCode) {
        fetchFromPref();
        final long now = new Date().getTime();

        if (_promptTime == -1) {
            _promptTime = now + startAfter;
            _incrementMultiplier = 2;
            storeToPref();
            return;
        }

        if (!isNetworkConnected()) {
            return;
        }

        if (_promptTime < now && (!_isAlreadyTaken || feedbackType == MULTIPLE_FEEDBACK)) {
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
                    }
                    Log.v(SS_VALIDATION, "survey validation error json" + jsonObject.toString() );
                    return;
                }
            } catch (Exception e) {
               Log.e(SS_VALIDATION, "Error in  processing  apiCallTask json" + e);
            } 
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(dialogTitle)
                    .setMessage(dialogMessage)
                    .setPositiveButton(dialogPositiveButtonText, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startSurveyForResult(requestCode);
                        }
                    })
                    .setNegativeButton(dialogNegativeButtonText, null);
            AlertDialog dialog = builder.create();
            dialog.show();

            _promptTime = now + (repeatInterval * (repeatType == REPEAT_TYPE_INCREMENTAL ? _incrementMultiplier : 1));
            _incrementMultiplier *= 2;
            storeToPref();
        }
    }

    /**
     * Clear scheduled survey.
     */
    public SurveySparrow clearSchedule() {
        _promptTime = -1;
        _isAlreadyTaken = false;
        _incrementMultiplier = 1;
        storeToPref();
        return this;
    }

    private void fetchFromPref() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(schedulePrefFileName, Context.MODE_PRIVATE);
        _isAlreadyTaken = sharedPreferences.getBoolean(SHARED_PREF_IS_TAKEN, false);
        _promptTime = sharedPreferences.getLong(SHARED_PREF_PROMPT_TIME, -1);
        _incrementMultiplier = sharedPreferences.getInt(SHARED_PREF_INCREMENT, 1);
    }

    private void storeToPref() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(schedulePrefFileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefEditor = sharedPreferences.edit();
        prefEditor.putLong(SHARED_PREF_PROMPT_TIME, _promptTime);
        prefEditor.putBoolean(SHARED_PREF_IS_TAKEN, _isAlreadyTaken);
        prefEditor.putInt(SHARED_PREF_INCREMENT, _incrementMultiplier);
        prefEditor.apply();
    }

    static void setAlreadyTaken(Context context, String token) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_FILE + "." + token, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefEditor = sharedPreferences.edit();
        prefEditor.putBoolean(SHARED_PREF_IS_TAKEN, true);
        prefEditor.apply();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}
