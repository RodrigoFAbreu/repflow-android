package com.repflow.app.presentation

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Asserts a button's label is fully rendered (design 9f, no mid-word wrap):
 * one line, no ellipsis, every character laid out, the text
 * wide enough inside its own node, and the text inside its button's bounds.
 * A one-line label that is merely cut off or ellipsized fails this, where a
 * line-count or "is displayed" check would pass.
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
    val textBounds = node.boundsInRoot
    val buttonBounds = buttonNode.fetchSemanticsNode().boundsInRoot
    assertTrue(
        "$tag is clipped by its button: text $textBounds, button $buttonBounds",
        textBounds.left >= buttonBounds.left - 1 &&
            textBounds.right <= buttonBounds.right + 1 &&
            textBounds.top >= buttonBounds.top - 1 &&
            textBounds.bottom <= buttonBounds.bottom + 1,
    )
}
