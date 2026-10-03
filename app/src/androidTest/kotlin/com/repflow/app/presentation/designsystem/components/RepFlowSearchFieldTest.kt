package com.repflow.app.presentation.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [RepFlowSearchField] (remediation-1-remediation-1 CP4, B5): exactly 48dp at
 * the default font scale, never clipping its text when the font is large, a
 * clear button, and the Search IME action.
 */
@RunWith(AndroidJUnit4::class)
class RepFlowSearchFieldTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var query by mutableStateOf("")

    private fun setField(fontScale: Float? = null) {
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = if (fontScale == null) density else Density(density.density, fontScale)
            CompositionLocalProvider(LocalDensity provides scaled) {
                RepFlowTheme {
                    RepFlowSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = HINT,
                        clearContentDescription = CLEAR,
                    )
                }
            }
        }
    }

    @Test
    fun theFieldIsExactly48dpTallAtTheDefaultFontScale() {
        setField(fontScale = 1f)

        composeRule.onNodeWithText(HINT).assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun atTwoHundredPercentFontScaleTheFieldGrowsAndTheTextIsNotClipped() {
        query = "Romanian Deadlift"
        setField(fontScale = 2f)

        val field = composeRule.onNodeWithText("Romanian Deadlift")
        field.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val boundsHeightPx = with(composeRule.density) { field.getUnclippedBoundsInRoot().let { it.bottom - it.top }.toPx() }
        val layouts = mutableListOf<TextLayoutResult>()
        field
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        val textHeightPx = layouts.single().size.height
        assertTrue(
            "text ($textHeightPx px) must fit inside the field ($boundsHeightPx px)",
            textHeightPx <= boundsHeightPx,
        )
    }

    @Test
    fun theClearButtonAppearsOnlyWithTextAndEmptiesTheQuery() {
        setField()
        composeRule.onNodeWithContentDescription(CLEAR).assertDoesNotExist()

        composeRule.onNodeWithText(HINT).performTextInput("bench")
        assertEquals("bench", query)
        composeRule.onNodeWithContentDescription(CLEAR).assertIsDisplayed().performClick()

        assertEquals("", query)
        composeRule.onNodeWithContentDescription(CLEAR).assertDoesNotExist()
    }

    @Test
    fun theImeActionIsSearch() {
        setField()

        composeRule.onNodeWithText(HINT).assert(
            SemanticsMatcher("ime action is Search") {
                it.config.getOrNull(SemanticsProperties.ImeAction) == ImeAction.Search
            },
        )
    }

    private companion object {
        const val HINT = "Search exercises"
        const val CLEAR = "Clear search"
    }
}
