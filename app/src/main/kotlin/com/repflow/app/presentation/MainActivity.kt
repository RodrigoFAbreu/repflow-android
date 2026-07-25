package com.repflow.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

/**
 * Application entry point activity.
 *
 * This currently renders only a minimal placeholder scaffold to prove the app
 * boots end-to-end (Hilt bootstrap + Compose rendering). Real screens are
 * introduced starting with the exercise-library vertical slice.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepFlowTheme {
                RepFlowRoot()
            }
        }
    }
}

@Composable
private fun RepFlowRoot() {
    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(text = "RepFlow")
        }
    }
}
