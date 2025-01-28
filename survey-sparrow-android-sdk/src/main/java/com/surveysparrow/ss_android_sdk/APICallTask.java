package com.surveysparrow.ss_android_sdk;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.surveysparrow.ss_android_sdk.SsSurvey.CustomParam;

public class APICallTask extends AsyncTask<String, Void, String> {

    private String apiUrl;
    private CustomParam[] customparam;
    private ApiCallback callback;
    public static final String SS_API_ERROR = "SS_API_ERROR";

    public APICallTask(String apiUrl, CustomParam[] customparam, ApiCallback callback) {
        this.apiUrl = apiUrl;
        this.callback = callback;
        this.customparam = customparam;
    }

    public static final String LOG_TAG = "SS_SAMPLE";
    @Override
    protected String doInBackground(String... params) {
        String urlString = params[0];
        String email = "";
        try {
            if (customparam != null) {
                for (CustomParam param : customparam) {
                    if( param.name.equals("emailaddress")){
                        email = param.value.toString();
                        break;
                    }
                }
            }
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain");
            String payloadData ="{\"email\":\"" + email + "\"}";
            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] payloadBytes = payloadData.getBytes(StandardCharsets.UTF_8);
                outputStream.write(payloadBytes, 0, payloadBytes.length);
            }
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    response.append(line);
                }
                bufferedReader.close();
                inputStream.close();
                callback.onResponse(response.toString());
                return response.toString();
            } else {
                 String errorMessage = getErrorMessage(connection);
                 Log.e(SS_API_ERROR, "connectiong error" + errorMessage);
                return "Error: " + responseCode;
            }
            
        } catch (IOException e) {
            Log.e(SS_API_ERROR, "response error" + e.getMessage());
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

    public CompletableFuture<String> executeAsync() {
        CompletableFuture<String> future = new CompletableFuture<>();
        execute(apiUrl);
        return future;
    }

    @Override
    protected void onPostExecute(String result) {
        // Complete the CompletableFuture when the AsyncTask completes
        CompletableFuture.completedFuture(result).thenAccept(callback::onResponse);
    }

    public interface ApiCallback {
        void onResponse(String response);
    }
}
