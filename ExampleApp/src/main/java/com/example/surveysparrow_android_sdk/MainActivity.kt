package com.example.surveysparrow_android_sdk

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.surveysparrow_android_sdk.ui.theme.SurveysparrowandroidsdkTheme
import com.surveysparrow.surveysparrow_android_sdk.SpotCheck
import com.surveysparrow.surveysparrow_android_sdk.SpotCheckConfig
import com.surveysparrow.surveysparrow_android_sdk.trackEvent
import com.surveysparrow.surveysparrow_android_sdk.trackScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SurveysparrowandroidsdkTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Main()
                }
            }
        }
    }
}

@Composable
fun Main() {

    val context = LocalContext.current

    val spotCheckConfig = remember {
        SpotCheckConfig(
            domainName = "gokul-spot.datasparrow.com",
            targetToken = "tar-pzv1GxZxWKJk7zJJbYSPyW",
            userDetails = hashMapOf(
                "mobile" to "6383846825",
            ),
            variables = mapOf(),
            customProperties = mapOf(),
            preferences = context.getSharedPreferences("spotcheck", Context.MODE_PRIVATE)
        )
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("spotCheck") { SpotCheckScreen(navController, spotCheckConfig) }
        composable("spotCheck2") { SpotCheckScreen2(navController, spotCheckConfig) }
    }
}
@Composable
fun HomeScreen(navController: NavController) {

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Text(text = "")
        Text(text = "")
        Text(text = "HomeScreen")
        Button(onClick = {
            navController.navigate("spotCheck")
        }) {
            Text(text = "Spot1")
        }
        Button(onClick = {
            navController.navigate("spotCheck2")
        }) {
            Text(text = "Spot2")
        }
        Text(text = "")
        Text(text = "")
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
