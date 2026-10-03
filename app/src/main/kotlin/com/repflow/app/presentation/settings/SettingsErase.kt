package com.repflow.app.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/** `4a`'s `Irreversible` card: the destructive ring, the warning, and `Erase all data` at 46. */
@Composable
internal fun EraseCard(
    enabled: Boolean,
    onEraseClick: () -> Unit,
) {
    val destructive = MaterialTheme.colorScheme.error
    Column(
        modifier =
            Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .border(BorderStroke(1.dp, destructive.copy(alpha = ERASE_CARD_RING_ALPHA)), EraseCardShape)
                .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
            Icon(
                painter = painterResource(RepFlowIcons.warning),
                contentDescription = null,
                tint = destructive,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.settings_erase_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = destructive,
            )
        }
        Spacer(Modifier.height(RepFlowSpacing.gapSm))
        Text(
            text = stringResource(R.string.settings_erase_body),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = EraseBodyFontSize, lineHeight = EraseBodyLineHeight),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        )
        Spacer(Modifier.height(RepFlowSpacing.gapLg))
        OutlinedButton(
            onClick = onEraseClick,
            enabled = enabled,
            shape = EraseButtonShape,
            border = BorderStroke(1.dp, destructive.copy(alpha = ERASE_BUTTON_RING_ALPHA)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = destructive),
            modifier = Modifier.fillMaxWidth().heightIn(min = EraseButtonMinHeight),
        ) {
            Icon(painter = painterResource(RepFlowIcons.trash), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(7.dp))
            Text(text = stringResource(R.string.settings_erase_action), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The typed confirmation in front of `Erase all data` (remediation-1 CP14 item
 * 5): it says what goes - a running workout included - and what stays - the
 * settings, and backup files already exported - and the destructive action is
 * enabled only once the confirmation word has been typed.
 */
@Composable
internal fun EraseAllDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val word = stringResource(R.string.settings_erase_confirm_word)
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_erase_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg)) {
                Text(stringResource(R.string.settings_erase_confirm_body))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.settings_erase_confirm_prompt, word)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isEraseConfirmationTyped(typed, word),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.settings_erase_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_erase_keep_action))
            }
        },
    )
}

/** Whether [typed] is the confirmation [word] - surrounding spaces and letter case aside. */
internal fun isEraseConfirmationTyped(
    typed: String,
    word: String,
): Boolean = typed.trim().equals(word, ignoreCase = true)

private val EraseCardShape = RoundedCornerShape(12.dp)
private val EraseButtonShape = RoundedCornerShape(9.dp)
private val EraseButtonMinHeight = 46.dp
private const val ERASE_CARD_RING_ALPHA = 0.28f
private const val ERASE_BUTTON_RING_ALPHA = 0.35f
private val EraseBodyFontSize = 13.sp
private val EraseBodyLineHeight = 19.5.sp
