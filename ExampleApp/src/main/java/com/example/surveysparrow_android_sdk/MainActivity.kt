package com.example.surveysparrow_android_sdk
import kotlinx.coroutines.delay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.surveysparrow_android_sdk.ui.theme.SurveysparrowandroidsdkTheme
import com.surveysparrow.surveysparrow_android_sdk.SpotCheck
import com.surveysparrow.surveysparrow_android_sdk.SpotCheckConfig
import com.surveysparrow.surveysparrow_android_sdk.SsSpotcheckListener
import com.surveysparrow.surveysparrow_android_sdk.trackEvent
import com.surveysparrow.surveysparrow_android_sdk.trackScreen

import com.surveysparrow.ss_android_sdk.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), OnSsResponseEventListener, OnSsValidateSurveyEventListener {
    
    companion object {
        const val SURVEY_REQUEST_CODE = 1
        const val LOG_TAG = "SS_SAMPLE"
        const val SS_DOMAIN = "your-domain-name"
        const val SS_TOKEN = "your-token"
        const val SS_LANG_CODE = "en"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SurveysparrowandroidsdkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Main()
                }
            }
        }
    }
    
    override fun onSsResponseEvent(response: JSONObject) {
        Log.v(LOG_TAG, "Survey Response: ${response.toString()}")
    }

    override fun onSsValidateSurvey(response: JSONObject) {
        Log.v(LOG_TAG, "Survey Validation: ${response.toString()}")
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SURVEY_REQUEST_CODE && resultCode == RESULT_OK) {
            val response = SurveySparrow.toJSON(data?.data.toString())
            Log.v(LOG_TAG, "Activity Result: ${response?.toString()}")
        }
    }
}

@Composable
fun Main() {

    val context = LocalContext.current
    val spotCheckListener = object : SsSpotcheckListener {
        override suspend fun onSurveyLoaded(response: Map<String, Any>) {
            println("Loaded: $response")
        }

        override suspend fun onSurveyResponse(response: Map<String, Any>) {
            println("Response: $response")
        }

        override suspend fun onPartialSubmission(response: Map<String, Any>) {
            println("Partial Submission: $response")
        }

        override suspend fun onCloseButtonTap() {
            println("User closed the SpotChecks")
        }

    }

    val spotCheckConfig = remember {
        SpotCheckConfig(
            domainName= "your-domain-name",
            targetToken= "your-token",
            userDetails = hashMapOf(),
            variables = mapOf(),
            customProperties = mapOf(),
            preferences = context.getSharedPreferences("spotcheck", Context.MODE_PRIVATE),
            spotCheckListener = spotCheckListener
        )
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("spotCheck") { SpotCheckScreen(navController, spotCheckConfig) }
        composable("spotCheck2") { SpotCheckScreen2(navController, spotCheckConfig) }
        composable("mobileSdk") { MobileSdkScreen(navController) }
        composable("embedSurvey") { EmbedSurveyScreen(navController) }
    }
}
@Composable
fun HomeScreen(navController: NavController) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Text(
            text = "Survey Sparrow SDK Demo",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Text(
            text = "SpotCheck SDK (Compose)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(onClick = {
            navController.navigate("spotCheck")
        }) {
            Text(text = "SpotCheck Example 1")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            navController.navigate("spotCheck2")
        }) {
            Text(text = "SpotCheck Example 2")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Mobile SDK (Activity)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(onClick = {
            navController.navigate("mobileSdk")
        }) {
            Text(text = "Mobile SDK Survey")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            navController.navigate("embedSurvey")
        }) {
            Text(text = "Embed Survey (Fragment)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotCheckScreen(navController: NavController, spotCheckConfig: SpotCheckConfig) {

    LaunchedEffect(Unit) {
        trackScreen(screen= "SpotCheckScreen", config = spotCheckConfig)
    }

    var isButtonClicked by remember { mutableStateOf(false) }

    if (isButtonClicked) {
        LaunchedEffect(true) {
            trackEvent(
                screen = "SpotCheckScreen",
                event = mapOf("MobileClick" to ""),
                config = spotCheckConfig
            )
            isButtonClicked = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("")
                },

                navigationIcon = {
                    IconButton(onClick = {navController.popBackStack()}) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            "back",
                        )
                    }
                }

            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {

            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Spot1",
                    modifier = Modifier.padding(30.dp)
                )
                Button(onClick = {
                    isButtonClicked = true
                }) {
                    Text(text = "Button")
                }
            }
        }
    }
    SpotCheck(spotCheckConfig)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotCheckScreen2(navController: NavController, spotCheckConfig: SpotCheckConfig) {

    LaunchedEffect(Unit) {
        trackScreen(screen= "SpotCheckScreen2", config = spotCheckConfig)
    }

    var isButtonClicked by remember { mutableStateOf(false) }


    if (isButtonClicked) {
        LaunchedEffect(Unit) {
            trackEvent(
                screen = "SpotCheckScreen2",
                event = mapOf("MobileClick" to ""),
                config = spotCheckConfig
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("")
                },

                navigationIcon = {
                    IconButton(onClick = {navController.popBackStack()}) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            "back",
                        )
                    }
                }

            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Spot2",
                    modifier = Modifier.padding(30.dp)
                )
                Button(onClick = {
                    isButtonClicked = true
                }) {
                    Text(text = "Button")
                }
            }
        }
    }

    SpotCheck(spotCheckConfig)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSdkScreen(navController: NavController) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Mobile SDK Example")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            "back",
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Mobile SDK",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Activity/Fragment-based surveys",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                Button(onClick = {
                    if (context is MainActivity) {
                        startMobileSdkSurvey(context)
                    }
                }) {
                    Text("Start Survey")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Check Logcat for survey responses",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

private fun startMobileSdkSurvey(activity: MainActivity) {
    val params = arrayOf(
        SsSurvey.CustomParam("emailaddress", "email@surveysparrow.com"),
        SsSurvey.CustomParam("email", "email@surveysparrow.com"),
        SsSurvey.CustomParam("url", "a")
    )
    val properties = hashMapOf("langCode" to MainActivity.SS_LANG_CODE)
    
    val survey = SsSurvey(
        MainActivity.SS_DOMAIN,
        MainActivity.SS_TOKEN,
        params,
        properties
    )
    
    val surveySparrow = SurveySparrow(activity, survey)
        .setAppBarTitle("Feedback Survey")
        .enableBackButton(true)
        .setWaitTime(2000)
        .setStartAfter(TimeUnit.DAYS.toMillis(3))
        .setRepeatInterval(TimeUnit.DAYS.toMillis(5))
        .setRepeatType(SurveySparrow.REPEAT_TYPE_CONSTANT)
        .setFeedbackType(SurveySparrow.SINGLE_FEEDBACK)
    
    surveySparrow.setValidateSurveyListener(activity)
    surveySparrow.startSurvey(MainActivity.SURVEY_REQUEST_CODE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbedSurveyScreen(navController: NavController) {
    val context = LocalContext.current
    val containerId = remember { android.view.View.generateViewId() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Embed Survey Example")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            "back",
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = {
                    if (context is MainActivity) {
                        loadEmbedSurvey(context, containerId)
                    }
                }) {
                    Text("Load Embed Survey")
                }
            }
            
            AndroidView(
                factory = { ctx ->
                    androidx.fragment.app.FragmentContainerView(ctx).apply {
                        id = containerId
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            )
        }
    }
}

private fun loadEmbedSurvey(activity: MainActivity, containerId: Int) {
    val params = arrayOf(
        SsSurvey.CustomParam("emailaddress", "email@surveysparrow.com"),
        SsSurvey.CustomParam("email", "email@surveysparrow.com"),
        SsSurvey.CustomParam("url", "a")
    )
    val properties = hashMapOf("langCode" to MainActivity.SS_LANG_CODE)
    
    val survey = SsSurvey(
        MainActivity.SS_DOMAIN,
        MainActivity.SS_TOKEN,
        params,
        properties
    )
    
    val fragment = SsSurveyFragment()
    fragment.setValidateSurveyListener(activity)
    fragment.setSurvey(survey)
    
    activity.supportFragmentManager.beginTransaction()
        .replace(containerId, fragment)
        .commit()
}
