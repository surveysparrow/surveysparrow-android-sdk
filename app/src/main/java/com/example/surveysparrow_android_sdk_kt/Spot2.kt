package com.example.surveysparrow_android_sdk_kt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import com.surveysparrow.surveysparrow_android_sdk.SpotCheck
import com.surveysparrow.surveysparrow_android_sdk.SpotCheckConfig
import com.surveysparrow.surveysparrow_android_sdk.trackEvent
import com.surveysparrow.surveysparrow_android_sdk.trackScreen


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
