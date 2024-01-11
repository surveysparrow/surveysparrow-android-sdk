# Survey Sparrow Android SDK

[SurveySparrow](https:://surveysparrow.com) Android SDK enables you to collect feedback from your mobile app. Embed the Classic, Chat & NPS surveys in your Android application seamlessly with few lines of code.

> Mobile SDK share channel is only available from SurveySparrow **Premium** plan onwards.

## Features
1. [Fully customizable pre-build `Activity` to take feedback whenever & wherever you want.](#Take-feedback-using-Activity)
2. [`Fragment` to integrate the feedback experience anywhere in your app.](#Embed-survey-in-your-Activity)
3. [Schedule Surveys to take one-time or recurring feedbacks.](#Schedule-Surveys)

## SDK integration (Require API level 19)

Add it in your **root** `build.gradle` at the end of repositories:
```gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

Add the following line to your **app modules** `build.gradle` file inside `dependencies`
```gradle
implementation 'com.github.surveysparrow:surveysparrow-android-sdk:0.4.8'
```

The SDK need Internet access to fetch survey & submit answers. Add the following permissions to `AndroidManifest.xml` file
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Take feedback using Activity
Take feedback using our pre-build `Activity` and get the response after submission by overriding the calling `Activity`'s `onActivityResult` method.

<img width="340" alt="SurveySparrow Android SDK full-screen view" src="https://user-images.githubusercontent.com/61273614/85107768-235faf80-b22c-11ea-85e2-1f043c47258e.png">

#### Create a [`SsSurvey`](#SsSurvey) Object
Create SDK token by from the share page of your survey in the SurveySparrow web app and use that token to create the `SsSurvey` Object.
```java
SsSurvey survey = new SsSurvey("your-company-domain", "sdk-token");
```
#### Create a [`SurveySparrow`](#SurveySparrow) object
Create a `SurveySparrow` object to configure and start the feedback Activity.
```java
SurveySparrow surveySparrow = new SurveySparrow(this, survey)
                .setActivityTheme(R.style.AppTheme)
                .setAppBarTitle(R.string.app_name)
                .enableBackButton(true)
                .setWaitTime(2000)
                .setValidateSurveyListener(this); // add this if you are using OnSsValidateSurveyEventListener in your activity
```

#### Start the feedback Activity
```java
surveySparrow.startSurveyForResult(SURVEY_REQUEST_CODE);
```

#### Handle response
Override the `onActivityResult` to handle the response.
```java
@Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable
  Intent data) {
  super.onActivityResult(requestCode, resultCode, data);
  if (requestCode == SURVEY_REQUEST_CODE) {
    if (resultCode == RESULT_OK) {
      // Convert the response to JSON.
      JSONObject responses = SurveySparrow.toJSON(data.getData().toString());

      // Add your logic here...
    } else {
      // The activity closed before completing the survey.
    }
  }
}
```

#### Handle survey validation
Override the `onSsValidateSurvey` to handle the survey validation.
```java
@Override
public void onSsValidateSurvey(JSONObject s) {
    Log.v(LOG_TAG, "survey validation json" + s.toString());
}
```

### Embed survey in your Activity
Embed the feedback experience to your `Activity` using the [`SsSurveyFragment`](#SsSurveyFragment). Then you can implement the `OnSsResponseEventListener` interface to handle response after submission.

Implement `OnSsValidateSurveyEventListener` interface to handle validation of survey

<img width="340" alt="SurveySparrow Android SDK embed view" src="https://user-images.githubusercontent.com/61273614/85107978-7b96b180-b22c-11ea-9e7e-381ff94992d8.png">

#### Create [`SsSurvey`](#SsSurvey) object
```java
SsSurvey survey = new SsSurvey("your-company-domain", "sdk-token");
```

#### Embed survey in your Activity
**For versions below 0.4.0**
```java
SsSurveyFragment surveyFragment = new SsSurveyFragment(survey);
```

**For other versions**
```java
SsSurveyFragment surveyFragment = new SsSurveyFragment();
surveyFragment.setValidateSurveyListener(this); // add this if you are using OnSsValidateSurveyEventListener in your activity
surveyFragment.setSurvey(survey);
```
#### Start the fragment transaction
```java
FragmentManager fragmentManager = this.getSupportFragmentManager();
FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
fragmentTransaction.add(R.id.surveyContainer, surveyFragment);
fragmentTransaction.commit();
```

#### Get survey response
Implement the [`OnSsResponseEventListener`](#OnSsResponseEventListener) interface in your Activity and override the `onSsResponseEvent` method to get and handle the response.

```java
public class YourActivity extends AppCompatActivity implements OnSsResponseEventListener {
  // Your code...
  @Override
    public void onSsResponseEvent(JSONObject response) {
        // Handle response here
    }
  // Your Code...
}
```
#### Handle survey validation
Override the `onSsValidateSurvey` to handle the survey validation.
```java
@Override
public void onSsValidateSurvey(JSONObject s) {
    Log.v(LOG_TAG, "survey validation json" + s.toString());
}
```

### Schedule Surveys
Ask the user to take a feedback survey when they open your app/Activity/page after a period of time.

<img width="340" alt="SurveySparrow Android SDK scheduling" src="https://user-images.githubusercontent.com/61273614/85107837-3bcfca00-b22c-11ea-91ac-94b685eefd70.png">

#### Create [`SsSurvey`](#SsSurvey) object
```java
SsSurvey survey = new SsSurvey("your-company-domain", "sdk-token");
```

#### Create and schedule the feedback activity.
```java
surveySparrow = new SurveySparrow(this, survey)
        .setActivityTheme(R.style.AppTheme)
        .setAppBarTitle(R.string.app_name)
        .enableBackButton(true)
        .setWaitTime(2000)
        .setValidateSurveyListener(this); // add this if you are using OnSsValidateSurveyEventListener in your activity

        // Schedule specific config
        .setStartAfter(TimeUnit.DAYS.toMillis(3L))
        .setRepeatInterval(TimeUnit.DAYS.toMillis(5L))
        .setRepeatType(SurveySparrow.REPEAT_TYPE_CONSTANT)
        .setFeedbackType(SurveySparrow.SINGLE_FEEDBACK);

surveySparrow.scheduleSurvey(SURVEY_SCHEDULE_REQUEST_CODE);
```

#### Handle response
Handle response the same way as [Take feedback using Activity](#Take-feedback-using-`Activity`)

#### Handle survey validation
Handle survey validation the same way as [Handle survey validation](#Handle-survey-validation`)


#### Clear a schedule
You can clear a schedule by calling the `surveySparrow.clearSchedule()` method.

#### How scheduling works
We will show a customized prompt to take a feedback survey whenever the `scheduleSurvey` method called after the `setStartAfter` time and if the user declines to take the survey we will show the prompt after the `setRepeatIntervalTime`.

**Example use case:** Add the above code to `onStart` method of your launcher Activity to ask the user to take a feedback survey 3 days after the user starts using your app, and if the user declines to take the survey we will continue to prompt at an interval of 5 days. If the user takes and complete the survey once we will not ask again.

**You can only create one schedule per token. Create multiple tokens if you want to create multiple schedules for same survey.*

### NPS Survey
Survey type can be changed to NPS using `setSurveyType(SurveySparrow.NPS)` on an `SsSurvey` instance. You can also pass email as a custom param.

## Reference
### SsSurvey
#### Nested classes
`CustomParam` : To hold custom params.

Constructor - `CustomParam(CharSequence name, CharSequence value)`

#### Public constructors
`SsSurvey(CharSequence domain, CharSequence surveyToken)` : Create SsSurvey with domain & token

`SsSurvey(CharSequence domain, CharSequence surveyToken, CustomParam[] customParams)` : Create SsSurvey with domain, token & custom params.

#### Public methods
|Return|Prototype|
|-----------|------|
|`SsSurvey`|`addCustomParam(CustomParam customParam)`: add custom param by passing a `SsSurvey.CustomParam` object.|
|`SsSurvey`|`addCustomParams(CustomParam[] customParams)`: add custom params by passing `SsSurvey.CustomParam` object array.
|`SsSurvey`|`addCustomParam(CharSequence name, CharSequence value)`: add custom param by passing name & value.
|`SsSurvey`|`setSurveyType(@SurveyType int surveyType)`: set survey type to CLASSIC/CHAT/NPS
|`SsSurvey`|`setThankYouRedirect(boolean thankyouRedirect)`: toggle redirect to thank you page outside the webview


### SurveySparrow
#### Public constants
|Type|Constant|
|-----------|------|
|`int`|`REPEAT_TYPE_CONSTANT`: Repeat survey with a constant interval.|
|`int`|`REPEAT_TYPE_INCREMENTAL`: Repeat survey with a incremental interval.|
|`int`|`SINGLE_FEEDBACK`: Collect schedule feedback for once.|
|`int`|`MULTIPLE_FEEDBACK`: Collect scheduled feedback multiple times. (Make sure that you have enabled 'Allow multiple submissions per user' for this survey)|
|`int`|`CLASSIC`: Classic survey type|
|`int`|`CHAT`: Chat survey type|
|`int`|`NPS`: NPS survey type|
|`String`|`SS_DEBUG_LOG`: Debug log tag.|

#### Public constructors
`SurveySparrow(Activity context, SsSurvey survey)` : Create SurveySparrow object with Activity context & SsSurvey

#### Static methods
|Return|Prototype|
|-----------|------|
|`JSONObject`|`toJSON(String text)`: Convert response text to JSONObject.|

#### Public methods
|Return|Prototype|
|-----------|------|
|`void`|`startSurveyForResult(int requestCode)`: Start feedback activity for result.|
|`SurveySparrow`|`setActivityTheme(@StyleRes int themeId)`: Set survey activity theme.|
|`SurveySparrow`|`setAppBarTitle(CharSequence title)`: Set survey Activity action bar title.|
|`SurveySparrow`|`setAppBarTitle(@StringRes int titleId)`: Set survey Activity action bar title.|
|`SurveySparrow`|`enableBackButton(boolean enable)`: Enable back button on the action bar of the survey activity.|
|`SurveySparrow`|`setWaitTime(long milliseconds)`: How long the activity should display the thank you page.|
|`SurveySparrow`|`setDialogTitle(CharSequence title)`: Set schedule alert dialog title.|
|`SurveySparrow`|`setDialogTitle(@StringRes int titleId)`: Set schedule alert dialog title.|
|`SurveySparrow`|`setDialogMessage(CharSequence message)`: Set schedule alert dialog message.|
|`SurveySparrow`|`setDialogMessage(@StringRes int messageId)`: Set schedule alert dialog message.|
|`SurveySparrow`|`setDialogPositiveButtonText(CharSequence text)`: Set schedule alert dialog positive button text.|
|`SurveySparrow`|`setDialogPositiveButtonText(@StringRes int textId)`: Set schedule alert dialog positive button text.|
|`SurveySparrow`|`setDialogNegativeButtonText(CharSequence text)`: Set schedule alert dialog negative button text.|
|`SurveySparrow`|`setDialogNegativeButtonText(@StringRes int textId)`: Set schedule alert dialog negative button text.|
|`SurveySparrow`|`setStartAfter(long milliseconds)`: Set the amount of time to wait before showing the scheduled dialog after launching the app for first time.|
|`SurveySparrow`|`setRepeatInterval(long milliseconds)`: Set the amount of time to wait to show the dialog once the user declined the dialog or accepted in the case of multiple feedback enabled.|
|`SurveySparrow`|`setRepeatType(@RepeatType int repeatType)`: Set schedule repeat type.|
|`SurveySparrow`|`setFeedbackType(@FeedbackType int feedbackType)`: Set schedule feedback type.|
|`SurveySparrow`|`scheduleSurvey(final int requestCode)`: Schedule a take survey dialog to show after opening the activity for first time, it will show again after the repeat interval if denied, even if the user accept to take the survey you can show the dialog again by setting feedback type to MULTIPLE_FEEDBACK.|
|`SurveySparrow`|`clearSchedule()`: Clear scheduled survey.|
|`void`|`enableDebugMode(boolean enable)`: Enable debug mode to view useful log messages while development. (Use only while development).|


### OnSsResponseEventListener
Implement `OnSsResponseEventListener` in your Activity to listen for survey response event.

#### Public methods
|Return|Prototype|
|-----------|------|
|`void`|`onSsResponseEvent(JSONObject data)`: Override this method to handle survey response event while using SsSurveyFragment.|

> Please submit bugs/issues through GitHub issues we will try to fix it ASAP.
