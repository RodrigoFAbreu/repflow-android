package com.repflow.app.presentation.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.presentation.backup.lastBackupLabel
import com.repflow.app.presentation.backup.lastBackupTitle
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.time.Instant
import java.time.ZoneId

/**
 * Settings, as design turn 8's `8c` draws it (functional review GF-4), in CP3's
 * sub-screen frame, grouped by when you would change something -
 *
 * - **Appearance** - `Theme` as three full-width segments (`Weight unit` is not
 *   built: `D5`, the domain stores kilograms only);
 * - **Rest timer** - auto-start, the `Default rest` row (opens a sheet),
 *   vibrate and notification switches;
 * - **During a workout** - keep screen awake, confirm before finishing and the
 *   `Extra set fields` row (opens a sheet);
 * - **Your data** - `Exercise library` (register `D25`), `Archived` (the
 *   Archived screen), `Backup and restore` (the Backup screen, `5d`, CP8) with
 *   its `Last backup <when>` subtitle, then the `Irreversible` card over
 *   `Erase all data`, behind a typed confirmation;
 * - the footer.
 *
 * Every switch, the theme and the two value rows render from
 * [SettingsUiState.settings] - what is stored - and wait, disabled, until it
 * has loaded. A switch's whole row is its tap target and it is announced as a
 * switch with its state. [now] and [zone] only date the Backup row's subtitle.
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    versionName: String?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showEraseConfirm by rememberSaveable { mutableStateOf(false) }
    var showRestSheet by rememberSaveable { mutableStateOf(false) }
    var showExtraSheet by rememberSaveable { mutableStateOf(false) }
    SettingsMessages(uiState = uiState, snackbarHostState = snackbarHostState, actions = actions)

    if (showEraseConfirm) {
        EraseAllDataDialog(
            onConfirm = {
                showEraseConfirm = false
                actions.onEraseAllDataConfirmed()
            },
            onDismiss = { showEraseConfirm = false },
        )
    }

    uiState.settings?.let { settings ->
        if (showRestSheet) {
            DefaultRestSheet(
                currentSeconds = settings.defaultRestSeconds,
                onSelect = actions.onDefaultRestSelected,
                onDismiss = { showRestSheet = false },
            )
        }
        if (showExtraSheet) {
            ExtraSetFieldsSheet(
                current = settings.extraSetFields,
                onSelect = actions.onExtraSetFieldsSelected,
                onDismiss = { showExtraSheet = false },
            )
        }
    }

    RepFlowScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = actions.onBack,
        backContentDescription = stringResource(R.string.settings_back_content_description),
        snackbarHost = { SnackbarHost(snackbarHostState) { SettingsSnackbar(it) } },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 20.dp),
        ) {
            SettingsGroups(
                settings = uiState.settings,
                actions = actions,
                onDefaultRestClick = { showRestSheet = true },
                onExtraSetFieldsClick = { showExtraSheet = true },
            )
            DataGroup(actions = actions, settings = uiState.settings, now = now, zone = zone)
            EraseCard(enabled = !uiState.isErasing, onEraseClick = { showEraseConfirm = true })
            Text(
                text =
                    versionName?.let { stringResource(R.string.settings_footer, it) }
                        ?: stringResource(R.string.settings_footer_no_version),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = FooterFontSize),
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            )
        }
    }
}

/** Settings' own messages: a save that failed, and the outcome of `Erase all data`. */
@Composable
private fun SettingsMessages(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions,
) {
    val erased = stringResource(R.string.settings_message_erased)
    val eraseFailed = stringResource(R.string.settings_message_erase_failed)
    val saveFailed = stringResource(R.string.settings_message_save_failed)

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        val text =
            when (message) {
                SettingsMessage.SAVE_FAILED -> saveFailed
                SettingsMessage.ERASED -> erased
                SettingsMessage.ERASE_FAILED -> eraseFailed
            }
        snackbarHostState.showSnackbar(text)
        actions.onSettingsMessageShown()
    }
}

@Composable
private fun SettingsGroups(
    settings: AppSettings?,
    actions: SettingsActions,
    onDefaultRestClick: () -> Unit,
    onExtraSetFieldsClick: () -> Unit,
) {
    val onToggle = actions.onToggle
    SectionLabel(R.string.settings_section_appearance, first = true)
    ThemeSegments(selected = settings?.theme, onSelect = actions.onThemeSelected)
    SectionLabel(R.string.settings_section_rest_timer)
    SettingsSwitchRow(
        R.string.settings_rest_auto_start,
        R.string.settings_rest_auto_start_meta,
        SettingToggle.REST_TIMER_AUTO_START,
        settings,
        onToggle,
    )
    SettingsValueRow(
        title = stringResource(R.string.settings_default_rest_row),
        meta = stringResource(R.string.settings_default_rest_row_meta),
        value = settings?.let { formatRest(it.defaultRestSeconds) },
        onClick = onDefaultRestClick,
    )
    SettingsSwitchRow(
        R.string.settings_rest_vibrate,
        null,
        SettingToggle.REST_TIMER_VIBRATE,
        settings,
        onToggle,
    )
    SettingsSwitchRow(
        R.string.settings_rest_notification,
        R.string.settings_rest_notification_meta,
        SettingToggle.REST_TIMER_NOTIFICATION,
        settings,
        onToggle,
    )
    SectionLabel(R.string.settings_section_workout)
    SettingsSwitchRow(
        R.string.settings_keep_screen_awake,
        null,
        SettingToggle.KEEP_SCREEN_AWAKE,
        settings,
        onToggle,
    )
    SettingsSwitchRow(
        R.string.settings_confirm_finish,
        R.string.settings_confirm_finish_meta,
        SettingToggle.CONFIRM_BEFORE_FINISHING,
        settings,
        onToggle,
    )
    SettingsValueRow(
        title = stringResource(R.string.settings_extra_set_fields_row),
        meta = stringResource(R.string.settings_extra_set_fields_row_meta),
        value = settings?.let { stringResource(extraSetFieldsLabelRes(it.extraSetFields)) },
        onClick = onExtraSetFieldsClick,
    )
}

@Composable
private fun DataGroup(
    actions: SettingsActions,
    settings: AppSettings?,
    now: Instant,
    zone: ZoneId,
) {
    SectionLabel(R.string.settings_section_data)
    SettingsActionRow(
        icon = RepFlowIcons.books,
        title = stringResource(R.string.settings_library_row),
        meta = stringResource(R.string.settings_library_meta),
        onClick = actions.onLibraryClick,
    )
    SettingsActionRow(
        icon = RepFlowIcons.archive,
        title = stringResource(R.string.settings_archived_row),
        meta = stringResource(R.string.settings_archived_row_meta),
        onClick = actions.onArchivedClick,
    )
    SettingsActionRow(
        icon = RepFlowIcons.database,
        title = stringResource(R.string.settings_backup_row),
        // `Last backup <when>` or `No backup yet` (`8c`), by the Backup screen's own label logic; nothing until settings load.
        meta =
            when {
                settings == null -> ""
                settings.lastBackupAt == null -> stringResource(R.string.backup_none_title)
                else -> lastBackupTitle(lastBackupLabel(settings.lastBackupAt, now, zone))
            },
        onClick = actions.onBackupClick,
    )
}

/** The design's toast card: the surface fill inside a hairline ring, radius 12. */
@Composable
private fun SettingsSnackbar(data: SnackbarData) {
    Snackbar(
        modifier =
            Modifier
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = RepFlowSpacing.gapMd)
                .border(1.dp, RepFlowColor.hairline, SnackbarShape),
        shape = SnackbarShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text = data.visuals.message, style = MaterialTheme.typography.bodyMedium)
    }
}

private val FooterFontSize = 12.sp
private val SnackbarShape = RoundedCornerShape(12.dp)
