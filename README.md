# SurveySparrow Android SDK

Use [SurveySparrow](https://surveysparrow.com) in your Android app to collect feedback: the **Normal SDK** embeds Classic, Chat, and NPS surveys in your UI, and **SpotChecks** lets you trigger surveys from screens and events you define. Both ship in one library.

**Latest release: [1.2.12](https://github.com/surveysparrow/surveysparrow-android-sdk/releases/tag/1.2.12)**.

## Add the dependency

Add JitPack to your root `build.gradle` repositories, then in your app module:

```gradle
implementation 'com.github.surveysparrow:surveysparrow-android-sdk:1.2.12'
```

**Requirements:** `minSdk` 24 or higher, `compileSdk` 34 or higher, a Jetpack Compose app for SpotChecks, and Android System WebView 80 or newer on the device.

**Upgrading to 1.2.12:** the SDK no longer depends on Retrofit or Gson. Add them to your own dependencies if your app relied on them transitively.

## Implementation guides

- **Normal SDK** – [developers.surveysparrow.com/sdk/android](https://developers.surveysparrow.com/sdk/android)
- **SpotChecks** – [developers.surveysparrow.com/spotchecks/mobile-spotchecks/android](https://developers.surveysparrow.com/spotchecks/mobile-spotchecks/android)

Report bugs and feature requests on [GitHub Issues](https://github.com/surveysparrow/surveysparrow-android-sdk/issues).
