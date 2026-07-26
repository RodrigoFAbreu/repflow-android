package com.repflow.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.repflow.app.presentation.navigation.RepFlowNavHost
import dagger.hilt.android.AndroidEntryPoint

/** Application entry point activity: Hilt bootstrap + the app's single [RepFlowNavHost]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepFlowTheme {
                RepFlowNavHost()
            }
        }
    }
}
