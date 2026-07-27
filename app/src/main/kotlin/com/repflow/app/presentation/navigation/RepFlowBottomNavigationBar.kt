package com.repflow.app.presentation.navigation

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/**
 * The app's bottom navigation bar (Milestone 8, CP1): one
 * [NavigationBarItem] per [RepFlowDestinations.TOP_LEVEL_DESTINATIONS]
 * entry, replacing the old ad-hoc `TextButton`s in `ExerciseListScreen`'s
 * app bar. [currentRoute] drives selected state; [onDestinationSelected]
 * is expected to navigate with `launchSingleTop`/`popUpTo`/`restoreState`
 * (see [RepFlowNavHost]) so switching tabs never duplicates back-stack
 * entries.
 */
@Composable
fun RepFlowBottomNavigationBar(
    currentRoute: String?,
    onDestinationSelected: (String) -> Unit,
) {
    NavigationBar {
        RepFlowDestinations.TOP_LEVEL_DESTINATIONS.forEach { destination ->
            val label = stringResource(destination.titleRes)
            val destinationContentDescription = stringResource(destination.contentDescriptionRes)
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination.route) },
                icon = { BasicText(destination.icon) },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.semantics { contentDescription = destinationContentDescription },
            )
        }
    }
}
