package com.repflow.app.presentation.navigation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.components.isDarkColorScheme

/**
 * The app's bottom navigation bar (Milestone 8, CP1): one
 * [NavigationBarItem] per [RepFlowDestinations.TOP_LEVEL_DESTINATIONS]
 * entry, replacing the old ad-hoc `TextButton`s in `ExerciseListScreen`'s
 * app bar. [currentRoute] drives selected state; [onDestinationSelected]
 * is expected to navigate with `launchSingleTop`/`popUpTo`/`restoreState`
 * (see [RepFlowNavHost]) so switching tabs never duplicates back-stack
 * entries.
 *
 * The visual foundation reaches this bar two ways. `surfaceContainer` (the
 * bar's own fill) and `secondary` (the selected label) arrive through the
 * `RepFlowTheme` cascade with nothing to do here; the other three roles are
 * overridden per item from [repFlowNavColors] - see it for why they are
 * scoped to this bar instead of assigned globally.
 */
@Composable
fun RepFlowBottomNavigationBar(
    currentRoute: String?,
    onDestinationSelected: (String) -> Unit,
) {
    val navColors = repFlowNavColors(MaterialTheme.colorScheme)
    val itemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = navColors.selectedIcon,
            indicatorColor = navColors.selectedIndicator,
            unselectedIconColor = navColors.unselected,
            unselectedTextColor = navColors.unselected,
        )
    NavigationBar {
        RepFlowDestinations.TOP_LEVEL_DESTINATIONS.forEach { destination ->
            val label = stringResource(destination.titleRes)
            val destinationContentDescription = stringResource(destination.contentDescriptionRes)
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination.route) },
                icon = {
                    Icon(
                        painter = painterResource(destination.icon),
                        // The accessible name is already set once, on the item's
                        // own semantics block below; a second description on the
                        // icon would land on that same merged node.
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.semantics { contentDescription = destinationContentDescription },
                colors = itemColors,
            )
        }
    }
}

/**
 * The three nav colours the design sets that the theme cascade deliberately
 * does not carry.
 *
 * Each is left at the Material 3 baseline globally because the role has other
 * consumers the design's value would break:
 *
 * - `onSurfaceVariant` ([unselected] icon + label) is also the resting text
 *   colour for 23 `OutlinedTextField`s and other stock components, where the
 *   design's opacity fails WCAG AA. Light uses 66% rather than the design's
 *   literal 55%: 55% composites to 3.34:1 against this bar's own container,
 *   short of the 4.5:1 an always-visible label owes.
 * - `secondaryContainer`/`onSecondaryContainer` ([selectedIndicator] pill and
 *   [selectedIcon]) would also retint three untouched screens' stock
 *   `FilterChip`s, whose selected label then measures 4.41:1.
 *
 * `ROLE_AUDIT.md` carries the full composite table. Every role not named here
 * is left to the cascade default (`Color.Unspecified`/`takeOrElse` in
 * `NavigationBarItemDefaults.colors`), so the selected label keeps reading
 * `secondary`.
 */
@Immutable
internal data class RepFlowNavColors(
    val unselected: Color,
    val selectedIndicator: Color,
    val selectedIcon: Color,
)

/**
 * Resolves [RepFlowNavColors] from the *applied* scheme rather than a second
 * `isSystemInDarkTheme()` call - the same honesty the design-system primitives
 * use, so a preview supplying the light scheme gets light's values.
 */
internal fun repFlowNavColors(scheme: ColorScheme): RepFlowNavColors =
    if (isDarkColorScheme(scheme)) {
        RepFlowNavColors(
            unselected = scheme.onSurface.copy(alpha = RepFlowColor.navUnselectedAlphaDark),
            selectedIndicator = scheme.primary.copy(alpha = RepFlowColor.navSelectedIndicatorAlphaDark),
            selectedIcon = RepFlowColor.accent300,
        )
    } else {
        RepFlowNavColors(
            unselected = scheme.onSurface.copy(alpha = RepFlowColor.navUnselectedAlphaLight),
            selectedIndicator = scheme.primary.copy(alpha = RepFlowColor.navSelectedIndicatorAlphaLight),
            selectedIcon = scheme.primary,
        )
    }
