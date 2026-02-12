package com.glycemicgpt.mobile.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glycemicgpt.mobile.presentation.navigation.GlycemicGptNavHost
import com.glycemicgpt.mobile.presentation.theme.GlycemicGptTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlycemicGptTheme {
                GlycemicGptNavHost()
            }
        }
    }
}
