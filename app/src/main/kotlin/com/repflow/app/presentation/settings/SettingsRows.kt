package com.repflow.app.presentation.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.isDarkColorScheme
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/** A Settings group's label - `4a`'s 11/500 uppercase, 24 above (8 for the first group), 2 below. */
@Composable
internal fun SectionLabel(
    @StringRes text: Int,
    first: Boolean = false,
) {
    RepFlowSectionLabel(
        text = stringResource(text),
        modifier = Modifier.padding(top = if (first) 8.dp else 24.dp, bottom = 2.dp),
    )
}

/**
 * `4a`'s switch row: label 14.5 over a 12.5 meta, the 44x26 track at the end,
 * at least 60 tall over an 8% divider. The whole row toggles, and it is a
 * switch to accessibility; until [settings] has loaded it is disabled and off.
 */
@Composable
internal fun SettingsSwitchRow(
    @StringRes title: Int,
    @StringRes meta: Int,
    toggle: SettingToggle,
    settings: AppSettings?,
    onToggle: (SettingToggle, Boolean) -> Unit,
) {
    val checked = settings?.isOn(toggle) == true
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsRowMinHeight)
                    .toggleable(
                        value = checked,
                        enabled = settings != null,
                        role = Role.Switch,
                        onValueChange = { on -> onToggle(toggle, on) },
                    ).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            RowText(stringResource(title), stringResource(meta), modifier = Modifier.weight(1f))
            SwitchTrack(checked)
        }
        RowDivider()
    }
}

@Composable
private fun SwitchTrack(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val knob = if (isDarkColorScheme(scheme)) scheme.onSurface else scheme.onPrimary
    Box(
        modifier =
            Modifier
                .size(width = SwitchTrackWidth, height = SwitchTrackHeight)
                .clip(CircleShape)
                .background(if (checked) scheme.primary else scheme.onSurface.copy(alpha = SWITCH_OFF_ALPHA)),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = if (checked) SwitchKnobOnX else SwitchKnobInset, y = SwitchKnobInset)
                    .size(SwitchKnobSize)
                    .clip(CircleShape)
                    .background(knob),
        )
    }
}

/** A navigating row: glyph at 60%, label over meta, a trailing caret. */
@Composable
internal fun SettingsActionRow(
    @DrawableRes icon: Int,
    title: String,
    meta: String,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsRowMinHeight)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = LEADING_ICON_ALPHA),
                modifier = Modifier.size(LeadingIconSize),
            )
            RowText(title, meta, modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(RepFlowIcons.caretRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.trailingCaretAlpha),
                modifier = Modifier.size(CaretSize),
            )
        }
        RowDivider()
    }
}

@Composable
internal fun RowText(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = RowTitleFontSize),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = ROW_DIVIDER_ALPHA))
}

internal val SettingsRowMinHeight = 60.dp
internal val RowTitleFontSize = 14.5.sp
private const val ROW_DIVIDER_ALPHA = 0.08f
private const val LEADING_ICON_ALPHA = 0.6f
private val LeadingIconSize = 19.dp
private val CaretSize = 16.dp
private val SwitchTrackWidth = 44.dp
private val SwitchTrackHeight = 26.dp
private val SwitchKnobSize = 22.dp
private val SwitchKnobInset = 2.dp
private val SwitchKnobOnX = 20.dp
private const val SWITCH_OFF_ALPHA = 0.18f
