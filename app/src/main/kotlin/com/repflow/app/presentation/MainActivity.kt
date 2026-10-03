package com.repflow.app.presentation

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.application.settings.ThemeMode
import com.repflow.app.presentation.navigation.RepFlowNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Application entry point activity: Hilt bootstrap + the app's single [RepFlowNavHost]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val themeModes by lazy {
        settingsRepository
            .observe()
            .map { it.theme }
            .distinctUntilChanged()
            .catch { failure ->
                if (failure is CancellationException) throw failure
                emit(ThemeMode.DEFAULT)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Until the stored mode loads the first frame follows the system (a registered cold-start flash).
            val themeMode by themeModes.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dark = isDarkTheme(themeMode, isSystemInDarkTheme())
            // The bars follow the applied scheme, not the system's, so a forced theme keeps legible icons.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
                )
                onDispose { }
            }
            RepFlowTheme(themeMode = themeMode) {
                RepFlowNavHost()
            }
        }
    }

    private companion object {
        /** `ComponentActivity`'s own default scrims for a 3-button navigation bar, kept as they were. */
        val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
