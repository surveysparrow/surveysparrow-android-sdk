package com.surveysparrow.ss_android_sdk;

import android.os.AsyncTask;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Semaphore;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import com.surveysparrow.ss_android_sdk.SsSurvey.CustomParam;

public class CloseSurvey extends AsyncTask < String, Void, String > {

    private String closeSurveyUrl;
    private String surveyToken;
    private int widgetContactId;
    private CloseSurveyCallback callback;
    private Semaphore semaphore;
    private Boolean surveyCompleted;
    public static final String SS_API_ERROR = "SS_API_ERROR";

    public CloseSurvey(String closeSurveyUrl, String surveyToken, int widgetContactId, Boolean surveyCompleted, CloseSurveyCallback callback) {
        this.closeSurveyUrl = closeSurveyUrl;
        this.surveyToken = surveyToken;
        this.callback = callback;
        this.widgetContactId = widgetContactId;
        this.surveyCompleted = surveyCompleted;
        this.semaphore = new Semaphore(0);
    }

    @Override
    protected String doInBackground(String...params) {
        String urlString = params[0];
        try {
            if (surveyToken.split("-")[0].equals("ntt") && widgetContactId != 0 && surveyCompleted != true) { // check if the survey is CX
                URL url = new URL(urlString);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                String throttledOn = sdf.format(new Date());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PUT");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "text/plain");
                String payloadData = "{\"throttledOn\":\"" + throttledOn + "\"}";
                try (OutputStream outputStream = connection.getOutputStream()) {
                    byte[] payloadBytes = payloadData.getBytes(StandardCharsets.UTF_8);
                    outputStream.write(payloadBytes, 0, payloadBytes.length);
                }
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String response = "{surveyClosed:true}";
                    callback.onResponse(response);
                    semaphore.release();
                    return response;
                } else {
                    String errorMessage = getErrorMessage(connection);
                    Log.e(SS_API_ERROR, "connection error in close survey" + errorMessage);
                    return "Error: " + responseCode;
                }
            } else {
                String response = "{surveyClosed:true}";
                callback.onResponse(response);
                semaphore.release();
                return response;
            }

        } catch (IOException e) {
            Log.e(SS_API_ERROR, "response error in close survey" + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private static String getErrorMessage(HttpURLConnection connection) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        StringBuilder errorMessage = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            errorMessage.append(line);
        }
        reader.close();
        return errorMessage.toString();
    }

    public void await () throws InterruptedException {
        semaphore.acquire();
    }
    public interface CloseSurveyCallback {
        void onResponse(String response);
    }
}