plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

val stringVersionName = "1.0.0-beta.2"

android {
    namespace = "com.surveysparrow.surveysparrow_android_sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven"){
                groupId = "com.surveysparrow"
                artifactId = "ss-android-sdk"
                version = stringVersionName
                pom {
                    name = "SurveySparrow Android SDK"
                    description = "SurveySparrow Android SDK enables you to collect feedback from your mobile app. Embed the Classic, Chat & NPS surveys in your Android application seamlessly with few lines of code."
                    url = "https://github.com/surveysparrow/surveysparrow-android-sdk"
                }
            }
        }
    }
}