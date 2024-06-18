package com.example.surveysparrow_android_sdk_kt

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.surveysparrow.surveysparrow_android_sdk.SpotCheckConfig

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

    val context = LocalContext.current

    val spotCheckConfig = remember {
        SpotCheckConfig(
            domainName = "rgk.ap.ngrok.io",
            targetToken = "tar-oZU5qvKuy63beDruYv3Dto",
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