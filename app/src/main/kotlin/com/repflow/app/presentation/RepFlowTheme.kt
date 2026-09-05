package com.repflow.app.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.repflow.app.presentation.designsystem.LocalRepFlowExtraColors
import com.repflow.app.presentation.designsystem.LocalRepFlowSpacingScale
import com.repflow.app.presentation.designsystem.RepFlowDarkColorScheme
import com.repflow.app.presentation.designsystem.RepFlowDarkExtraColors
import com.repflow.app.presentation.designsystem.RepFlowDefaultSpacingScale
import com.repflow.app.presentation.designsystem.RepFlowLightColorScheme
import com.repflow.app.presentation.designsystem.RepFlowLightExtraColors
import com.repflow.app.presentation.designsystem.RepFlowShapeScheme
import com.repflow.app.presentation.designsystem.RepFlowTypography

/**
 * The app's Material 3 theme, built from the RepFlow design tokens in
 * `presentation/designsystem`.
 *
 * Every screen already renders through stock Material 3 components reading
 * `MaterialTheme`, so swapping the schemes here re-colours and re-types the
 * whole app without touching another file. The two tokens Material 3 has no
 * slot for (`control`, `hairline`) and the spacing scale ride alongside it as
 * composition locals.
 */
@Composable
fun RepFlowTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalRepFlowExtraColors provides
            if (darkTheme) RepFlowDarkExtraColors else RepFlowLightExtraColors,
        LocalRepFlowSpacingScale provides RepFlowDefaultSpacingScale,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) RepFlowDarkColorScheme else RepFlowLightColorScheme,
            typography = RepFlowTypography,
            shapes = RepFlowShapeScheme,
            content = content,
        )
    }
}
