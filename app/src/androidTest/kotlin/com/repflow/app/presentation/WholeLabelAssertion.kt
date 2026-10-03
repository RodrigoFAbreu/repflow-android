package com.repflow.app.presentation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Asserts a button's label is fully rendered (design 9f, no mid-word wrap).
 * Each check catches a different failure, and none is redundant:
 * - one line: catches a mid-word or word wrap;
 * - no ellipsis and every character laid out: catches a label truncated by
 *   `maxLines`/`overflow`;
 * - line right edge within the text node's width: catches horizontal
 *   overflow of the text box;
 * - line bottom within the text node's height: catches a label cut off
 *   vertically (a fixed-height button at large font keeps one line, every
 *   character and no ellipsis, yet the text node is shorter than the line);
 * - the text node's unclipped rectangle inside its button's unclipped
 *   rectangle: catches a text box that sticks out of its button. Unclipped
 *   bounds are used on purpose, because `boundsInRoot` is already
 *   intersected with ancestor clips and so can never show an overflow.
 */
internal fun ComposeTestRule.assertButtonLabelWhole(
    label: String,
    context: String,
) {
    val textNode = onNode(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
    val buttonNode = onNode(hasClickAction() and hasText(label))
    val results = mutableListOf<TextLayoutResult>()
    textNode
        .fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action
        ?.invoke(results)
    val layout = results.single()
    val text = layout.layoutInput.text.text
    val tag = "label '$label' ($context)"
    assertEquals("$tag wraps", 1, layout.lineCount)
    assertFalse("$tag is ellipsized", layout.isLineEllipsized(0))
    assertEquals("$tag is missing characters", text.length, layout.getLineEnd(0, visibleEnd = true))
    val node = textNode.fetchSemanticsNode()
    assertTrue(
        "$tag is wider (${layout.getLineRight(0)}px) than its text box (${node.size.width}px)",
        layout.getLineRight(0) <= node.size.width + 1,
    )
    assertTrue(
        "$tag is cut off vertically (line bottom ${layout.getLineBottom(0)}px, text box ${node.size.height}px)",
        layout.getLineBottom(0) <= node.size.height + 1,
    )
    val textBounds = node.unclippedBounds()
    val buttonBounds = buttonNode.fetchSemanticsNode().unclippedBounds()
    assertTrue(
        "$tag is clipped by its button: text $textBounds, button $buttonBounds",
        textBounds.left >= buttonBounds.left - 1 &&
            textBounds.right <= buttonBounds.right + 1 &&
            textBounds.top >= buttonBounds.top - 1 &&
            textBounds.bottom <= buttonBounds.bottom + 1,
    )
}

/** Layout rectangle in root coordinates, without the ancestor clipping `boundsInRoot` applies. */
private fun SemanticsNode.unclippedBounds(): Rect = Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))
