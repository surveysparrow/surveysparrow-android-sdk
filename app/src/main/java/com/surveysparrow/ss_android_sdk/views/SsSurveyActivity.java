package com.surveysparrow.ss_android_sdk.views;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import com.surveysparrow.ss_android_sdk.R;
import com.surveysparrow.ss_android_sdk.SurveySparrow;
import com.surveysparrow.ss_android_sdk.helpers.OnSsResponseEventListener;
import com.surveysparrow.ss_android_sdk.models.SsSurvey;

public final class SsSurveyActivity extends AppCompatActivity implements OnSsResponseEventListener {
    private boolean isSurveyComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        setTheme(intent.getIntExtra(SurveySparrow.SS_ACTIVITY_THME, R.style.SurveyTheme));
        setContentView(R.layout.activity_ss_survey);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(intent.getStringExtra(SurveySparrow.SS_APPBAR_TITLE));
            actionBar.setDisplayHomeAsUpEnabled(intent.getBooleanExtra(SurveySparrow.SS_BACK_BUTTON, true));
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        SsSurveyFragment surveyFragment = new SsSurveyFragment((SsSurvey) intent.getSerializableExtra(SurveySparrow.SS_SURVEY));
        fragmentTransaction.add(R.id.surveyContainer, surveyFragment);
        fragmentTransaction.commit();
    }

    @Override
    public void responseEvent(String data) {
        this.isSurveyComplete = true;
        Intent resultIntent = new Intent();
        resultIntent.setData(Uri.parse(data));
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (!this.isSurveyComplete) {
            setResult(RESULT_CANCELED);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
