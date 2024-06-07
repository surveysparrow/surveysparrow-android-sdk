package com.example.surveysparrow_android_sdk_kt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.surveysparrow.surveysparrow_android_sdk.SpotCheck

import com.surveysparrow.surveysparrow_android_sdk.SpotCheckConfig
import com.surveysparrow.surveysparrow_android_sdk.trackEvent
import com.surveysparrow.surveysparrow_android_sdk.trackScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Main()
            }
        }
    }
}

@Composable
fun Main() {
    val spotCheckConfig = remember {
        SpotCheckConfig(
            email = "gokulkrishna.raju@surveysparrow.com",
            domainName = "sushmitha.datasparrow.com",
            targetToken = "tar-rpfpo2q6MqMc9QWZRdgLAf",
            firstName = "gokulkrishna",
            lastName = "raju",
            phoneNumber = "6383846825",
            variables = mapOf(),
            customProperties = mapOf()
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