package com.repflow.app.presentation.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

// Placeholder - replaced by remediation-1 CP14 (the grouped Settings screen).

/**
 * The `SETTINGS` route until CP14 builds the design's grouped screen.
 *
 * It carries the two entry rows the four-tab bar's relocations depend on
 * (remediation-1 CP2 item 4), so neither destination is stranded while it is
 * no longer a tab:
 *
 * - `Library` ([onLibraryClick]), wearing `books` - the exercise library's
 *   inward path (register `D25`). CP14's `Library` row absorbs it;
 * - `Backup and restore` ([onBackupClick]), wearing `cloud-arrow-up` - the
 *   backup screen's only inward path. CP14's `Data` group absorbs it.
 *
 * Settings is not a tab, so the bottom bar is absent here (`4a`'s
 * `nShowNav = nTab !== 'settings'`); [onBackClick] is the way out besides
 * system back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPlaceholder(
    onBackClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onBackupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(RepFlowIcons.arrowLeft),
                            contentDescription = stringResource(R.string.settings_back_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RepFlowSpacing.screenPadding),
        ) {
            SettingsPlaceholderRow(
                icon = RepFlowIcons.books,
                title = stringResource(R.string.settings_placeholder_library),
                meta = stringResource(R.string.settings_placeholder_library_meta),
                onClick = onLibraryClick,
            )
            HorizontalDivider()
            SettingsPlaceholderRow(
                icon = RepFlowIcons.cloudArrowUp,
                title = stringResource(R.string.settings_placeholder_backup),
                meta = stringResource(R.string.settings_placeholder_backup_meta),
                onClick = onBackupClick,
            )
        }
    }
}

/** One navigation row: glyph, title over meta, trailing caret; the whole row is the tap target. */
@Composable
private fun SettingsPlaceholderRow(
    @DrawableRes icon: Int,
    title: String,
    meta: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = RepFlowSpacing.gapMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(RepFlowSpacing.gapLg))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.settings_placeholder_row_caret),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
