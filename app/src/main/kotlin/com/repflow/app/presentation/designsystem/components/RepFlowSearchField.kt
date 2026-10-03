package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/**
 * `2c`'s search field, shared by the Library and both exercise pickers
 * (remediation-1-remediation-1 CP4, B5).
 *
 * Material's `OutlinedTextField` carries its own 56dp minimum, which made the
 * `heightIn(min = 48.dp)` each caller used a no-op. This is a `BasicTextField`
 * decorated by `OutlinedTextFieldDefaults.DecorationBox`, which has no minimum
 * of its own: the field is exactly 48dp at the default font scale and grows
 * past it only when a larger font needs the room, so the text never clips.
 * Radius 10, a hairline, a leading magnifier, 14.5sp text, and a clear button
 * (48dp hit area, visible only once there is something to clear).
 *
 * @param containerColor the fill behind the hairline: `surface` on a screen,
 *   `control` on a sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepFlowSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val keyboard = LocalSoftwareKeyboardController.current
    val secondary = repFlowSecondaryTextColor(scheme)
    val colors =
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            unfocusedBorderColor = RepFlowColor.hairline,
            focusedBorderColor = scheme.primary,
        )
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().heightIn(min = SearchFieldHeight),
        textStyle = LocalTextStyle.current.copy(color = scheme.onSurface, fontSize = SearchTextSize),
        singleLine = true,
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    Text(text = placeholder, style = LocalTextStyle.current.copy(fontSize = SearchTextSize), color = secondary)
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(RepFlowIcons.magnifyingGlass),
                        contentDescription = null,
                        tint = secondary,
                        modifier = Modifier.size(SearchGlyphSize),
                    )
                },
                trailingIcon =
                    if (query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.semantics { contentDescription = clearContentDescription },
                            ) {
                                Icon(
                                    painter = painterResource(RepFlowIcons.xCircle),
                                    contentDescription = null,
                                    tint = secondary,
                                    modifier = Modifier.size(SearchGlyphSize),
                                )
                            }
                        }
                    } else {
                        null
                    },
                colors = colors,
                contentPadding = PaddingValues(horizontal = SearchHorizontalPadding, vertical = SearchVerticalPadding),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = SearchFieldShape,
                    )
                },
            )
        },
    )
}

/** `2c`: 48 tall, radius 10, 14.5 text, an 18 glyph. */
private val SearchFieldHeight = 48.dp
private val SearchFieldShape = RoundedCornerShape(10.dp)
private val SearchTextSize = 14.5.sp
private val SearchGlyphSize = 18.dp
private val SearchVerticalPadding = 8.dp
private val SearchHorizontalPadding = 12.dp
