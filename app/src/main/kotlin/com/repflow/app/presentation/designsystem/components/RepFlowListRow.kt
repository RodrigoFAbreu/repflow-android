// Named after the composable it holds, the Compose convention; its one
// top-level object is that composable's `...Defaults`.
@file:Suppress("MatchingDeclarationName")

package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/*
 * The row archetype behind the library, plans, history and settings lists
 * (`6b`: "Rows are 56-68 tall"; History's row, `RepFlow.dc.html:958-968`):
 * an optional leading slot, a 15/500 title over a 12.5 meta line, and a
 * trailing slot - by default the 30% `caret-right` a navigating row carries -
 * over a 9% divider. The whole row is the tap target.
 */

object RepFlowListRowDefaults {
    /** The bottom of `6b`'s 56-68 range: settings and sheet rows. */
    val minHeight = 56.dp

    /** The top of the range: a two-line row with tabular meta (History, Plans). */
    val tallMinHeight = 68.dp

    /** `padding:13px 2px` - the row's own inset; the screen supplies the 16. */
    val verticalPadding = 13.dp
    val horizontalPadding = 2.dp

    /** `gap:12px` between leading, text and trailing. */
    val gap = 12.dp

    /** `margin-top:3px` between title and meta. */
    val metaGap = 3.dp

    val trailingCaretSize = 16.dp
    val dividerThickness = 1.dp
}

/**
 * One list row.
 *
 * @param onClick when non-null the whole row is a button, and [trailing]
 *   defaults to the caret; when null the row is inert and shows no caret.
 * @param leading an optional glyph or marker before the text.
 * @param trailing replaces the default caret - an action, a status chip, a
 *   switch.
 */
@Composable
fun RepFlowListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = RepFlowListRowDefaults.minHeight,
    showDivider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
                    .padding(
                        horizontal = RepFlowListRowDefaults.horizontalPadding,
                        vertical = RepFlowListRowDefaults.verticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowListRowDefaults.gap),
        ) {
            leading?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RepFlowListRowDefaults.metaGap),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta != null) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    )
                }
            }
            when {
                trailing != null -> {
                    trailing()
                }

                onClick != null -> {
                    Icon(
                        painter = painterResource(RepFlowIcons.caretRight),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.trailingCaretAlpha),
                        modifier = Modifier.size(RepFlowListRowDefaults.trailingCaretSize),
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = RepFlowListRowDefaults.dividerThickness,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha),
            )
        }
    }
}
