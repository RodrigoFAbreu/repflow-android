package com.repflow.app.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.settings.ThemeMode
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowNumericKeypad
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/*
 * Settings' choosers (`5c`, remediation-1-remediation-1 CP7): `Theme`'s three
 * full-width segments, the value rows that open a sheet, and the two sheets
 * (`Default rest`, `Extra set fields`). `5c` draws the rows and not the sheets;
 * the sheets follow `6b` ("sheets for choices") and design turn 7 `7c` N2/N3.
 */

/** `5c`'s `Theme`: the label over three equal segments, each 44 tall at radius 8, the chosen one tinted. */
@Composable
internal fun ThemeSegments(
    selected: ThemeMode?,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = stringResource(R.string.settings_theme_label),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = RowTitleFontSize),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
        ) {
            ThemeMode.entries.forEach { mode ->
                Segment(
                    label = stringResource(themeLabelRes(mode)),
                    selected = mode == selected,
                    enabled = selected != null,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val shape = MaterialTheme.shapes.small
    Box(
        modifier =
            modifier
                .heightIn(min = SegmentMinHeight)
                .clip(shape)
                .background(if (selected) colors.fill else Color.Transparent, shape)
                .border(BorderStroke(1.dp, if (selected) colors.border else RepFlowColor.hairline), shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = SegmentFontSize),
            color = if (selected) colors.label else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@StringRes
internal fun themeLabelRes(mode: ThemeMode): Int =
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

/**
 * `5c`'s `Default rest` / `Extra set fields` row: label over meta, the current
 * value in the accent beside a caret, the whole row a button that opens a sheet.
 * Disabled until the settings have loaded ([value] `null`).
 */
@Composable
internal fun SettingsValueRow(
    title: String,
    meta: String,
    value: String?,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.settings_value_content_description, title, value.orEmpty())
    Column(modifier = Modifier.alpha(if (value != null) 1f else DISABLED_VALUE_ALPHA)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsRowMinHeight)
                    .clickable(enabled = value != null, role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = description }
                    .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            RowText(title, meta, modifier = Modifier.weight(1f))
            Text(
                text = value.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = ValueFontSize, fontFeatureSettings = "tnum"),
                color = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
            )
            Icon(
                painter = painterResource(RepFlowIcons.caretRight),
                contentDescription = null,
                tint = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
                modifier = Modifier.size(ValueCaretSize),
            )
        }
        RowDivider()
    }
}

/** A rest as `m:ss`, the way `5c` shows `2:00`. */
internal fun formatRest(seconds: Int): String = "${seconds / SECONDS_PER_MINUTE}:%02d".format(seconds % SECONDS_PER_MINUTE)

/** The values `Default rest`'s sheet offers as chips, in the design's order (`7c` N2). */
internal val DefaultRestPresets: List<Int> = listOf(ONE_MINUTE, ONE_AND_A_HALF_MINUTES, TWO_MINUTES, THREE_MINUTES)

/**
 * `Default rest`'s sheet (`7c` N2): a title and caption over the four presets
 * and `Other`. A preset saves and closes; `Other` opens the existing numeric
 * keypad (whole seconds; the keypad itself is unchanged), and a value that is
 * not a preset then stands in `Other`'s place as a selected chip - tapping it
 * reopens the keypad.
 */
@Composable
internal fun DefaultRestSheet(
    currentSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    if (keypadOpen) {
        RepFlowNumericKeypad(
            title = stringResource(R.string.settings_default_rest_keypad_title),
            allowDecimal = false,
            onDismissRequest = { keypadOpen = false },
            onConfirm = { value ->
                onSelect(value.toInt())
                onDismiss()
            },
        )
        return
    }
    RepFlowSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = RepFlowSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            Text(
                text = stringResource(R.string.settings_default_rest_sheet_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = SheetTitleFontSize),
            )
            Text(
                text = stringResource(R.string.settings_default_rest_sheet_caption),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            )
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
            ) {
                DefaultRestPresets.forEach { preset ->
                    Segment(
                        label = formatRest(preset),
                        selected = preset == currentSeconds,
                        enabled = true,
                        onClick = {
                            onSelect(preset)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val custom = currentSeconds !in DefaultRestPresets
            Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                Segment(
                    label = if (custom) formatRest(currentSeconds) else stringResource(R.string.settings_default_rest_other),
                    selected = custom,
                    enabled = true,
                    onClick = { keypadOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** `Extra set fields`' sheet (`7c` N3): a radio per mode with its explanation; a pick saves and closes. */
@Composable
internal fun ExtraSetFieldsSheet(
    current: ExtraSetFields,
    onSelect: (ExtraSetFields) -> Unit,
    onDismiss: () -> Unit,
) {
    RepFlowSheet(onDismissRequest = onDismiss, title = stringResource(R.string.settings_extra_set_fields_sheet_title)) {
        Column(modifier = Modifier.selectableGroup()) {
            ExtraSetFields.entries.forEach { mode ->
                ChoiceRow(
                    title = stringResource(extraSetFieldsLabelRes(mode)),
                    meta = stringResource(extraSetFieldsMetaRes(mode)),
                    selected = mode == current,
                    onClick = {
                        onSelect(mode)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    meta: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ChoiceRowMinHeight)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_ALPHA) else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
    ) {
        RowText(title, meta, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                painter = painterResource(RepFlowIcons.checkFat),
                contentDescription = null,
                tint = colors.label,
                modifier = Modifier.size(ValueCaretSize),
            )
        }
    }
}

@StringRes
internal fun extraSetFieldsLabelRes(mode: ExtraSetFields): Int =
    when (mode) {
        ExtraSetFields.ALWAYS_SHOWN -> R.string.settings_extra_set_fields_always
        ExtraSetFields.COLLAPSED -> R.string.settings_extra_set_fields_collapsed
        ExtraSetFields.OFF -> R.string.settings_extra_set_fields_off
    }

@StringRes
private fun extraSetFieldsMetaRes(mode: ExtraSetFields): Int =
    when (mode) {
        ExtraSetFields.ALWAYS_SHOWN -> R.string.settings_extra_set_fields_always_meta
        ExtraSetFields.COLLAPSED -> R.string.settings_extra_set_fields_collapsed_meta
        ExtraSetFields.OFF -> R.string.settings_extra_set_fields_off_meta
    }

private const val SECONDS_PER_MINUTE = 60
private const val ONE_MINUTE = SECONDS_PER_MINUTE
private const val ONE_AND_A_HALF_MINUTES = 90
private const val TWO_MINUTES = 2 * SECONDS_PER_MINUTE
private const val THREE_MINUTES = 3 * SECONDS_PER_MINUTE
private const val DISABLED_VALUE_ALPHA = 0.38f
private const val SELECTED_ROW_ALPHA = 0.10f
private val SegmentMinHeight = 44.dp
private val SegmentFontSize = 13.5.sp
private val ValueFontSize = 14.5.sp
private val ValueCaretSize = 14.dp
private val SheetTitleFontSize = 18.sp
private val ChoiceRowMinHeight = 60.dp
