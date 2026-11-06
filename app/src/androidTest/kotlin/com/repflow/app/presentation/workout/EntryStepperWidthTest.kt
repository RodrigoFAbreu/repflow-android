package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * The stepper cards change arrangement at 400 dp, so a 384 dp phone crosses that
 * width each time it rotates. An open pad has to keep its one entry through that,
 * and nothing an earlier width saved may come back (R3).
 */
@RunWith(AndroidJUnit4::class)
class EntryStepperWidthTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Plain, not state, so a new width first applies to the composition after a
     * restore. A real rotation is the same: the state is saved at the old width
     * and restored at the new one.
     */
    private var width = NARROW
    private var load by mutableStateOf<BigDecimal?>(BigDecimal("10"))
    private var reps by mutableStateOf<BigDecimal?>(BigDecimal("12"))
    private lateinit var tester: StateRestorationTester

    private fun string(
        @StringRes id: Int,
    ) = composeRule.activity.getString(id)

    private fun setCards(
        startWidth: Dp,
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
    ) {
        width = startWidth
        tester = StateRestorationTester(composeRule)
        tester.setContent {
            RepFlowTheme {
                // `requiredWidth`: the wide width is more than a 384 dp window allows.
                Box(Modifier.requiredWidth(width)) {
                    EntrySteppers(
                        trackingType = trackingType,
                        load = load,
                        onLoadChange = { load = it },
                        reps = reps,
                        onRepsChange = { reps = it },
                        seconds = BigDecimal(90),
                        onSecondsChange = {},
                        loadStep = BigDecimal("2.5"),
                        repRange = null,
                    )
                }
            }
        }
    }

    private fun crossTo(newWidth: Dp) {
        width = newWidth
        tester.emulateSavedInstanceStateRestore()
    }

    private fun press(vararg keys: String) = keys.forEach { composeRule.onNodeWithText(it).performClick() }

    /** Backspace's label, because a whole-number pad's caption doesn't say `Selected`. */
    private fun assertSelected(value: String) {
        composeRule.onNode(hasText(value) and isSelected()).assertIsSelected()
        composeRule.onNodeWithContentDescription(string(R.string.repflow_keypad_clear_content_description)).assertIsDisplayed()
    }

    private fun assertTyped(value: String) {
        composeRule.onNodeWithText(value).assertIsDisplayed()
        composeRule.onNode(hasText(value) and isSelected()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.repflow_keypad_backspace_content_description)).assertIsDisplayed()
    }

    private fun assertPadClosed() {
        composeRule.onNodeWithText(string(R.string.repflow_keypad_confirm)).assertDoesNotExist()
    }

    private fun pressSet() {
        composeRule.onNodeWithText(string(R.string.repflow_keypad_confirm)).performClick()
        composeRule.waitForIdle()
    }

    /** Closes a pad that stayed open, so a test goes on to what the old width saved. */
    private fun closePadIfOpen() {
        val cancel = composeRule.onAllNodesWithText(string(R.string.repflow_keypad_cancel))
        if (cancel.fetchSemanticsNodes().isNotEmpty()) {
            cancel.onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    private fun boundsOf(
        @StringRes contentDescription: Int,
    ): DpRect =
        composeRule
            .onNodeWithContentDescription(string(contentDescription))
            .getUnclippedBoundsInRoot()
            .let { DpRect(it.left, it.top, it.right, it.bottom) }

    @Test
    fun aSelectedWeightStaysSelectedGoingWideAndComingBack() {
        setCards(NARROW)
        press("10")
        assertSelected("10")
        crossTo(WIDE)
        assertSelected("10")
        crossTo(NARROW)
        assertSelected("10")
        pressSet()
        assertEquals(0, BigDecimal("10").compareTo(load))
        assertPadClosed()
    }

    @Test
    fun aSelectedRepsValueStaysSelectedGoingNarrowAndComingBack() {
        setCards(WIDE)
        press("12")
        assertSelected("12")
        crossTo(NARROW)
        assertSelected("12")
        crossTo(WIDE)
        assertSelected("12")
    }

    @Test
    fun aTypedWeightStaysTypedGoingNarrowAndComingBack() {
        setCards(WIDE)
        press("10", "4", "5")
        assertTyped("45")
        crossTo(NARROW)
        assertTyped("45")
        crossTo(WIDE)
        assertTyped("45")
        pressSet()
        assertEquals(0, BigDecimal("45").compareTo(load))
    }

    @Test
    fun aTypedRepsValueStaysTypedGoingWideAndComingBack() {
        setCards(NARROW)
        press("12", "3", "4")
        assertTyped("34")
        crossTo(WIDE)
        assertTyped("34")
        crossTo(NARROW)
        assertTyped("34")
    }

    @Test
    fun thereIsOneEntryWhicheverWidthItIsTypedAt() {
        setCards(NARROW)
        press("10", "4")
        crossTo(WIDE)
        press("5")
        assertTyped("45")
        crossTo(NARROW)
        press("6")
        assertTyped("456")
        pressSet()
        assertEquals(0, BigDecimal("456").compareTo(load))
    }

    @Test
    fun aSelectedValueDoesNotComeBackOverAValueThatChanged() {
        setCards(NARROW)
        press("10")
        crossTo(WIDE)
        closePadIfOpen()
        repeat(2) { composeRule.onNodeWithContentDescription(string(R.string.workout_focus_more_weight)).performClick() }
        composeRule.onNodeWithText("15").assertIsDisplayed()
        crossTo(NARROW)
        assertPadClosed()
        composeRule.onNodeWithText("15").assertIsDisplayed()
        assertEquals(0, BigDecimal("15").compareTo(load))
    }

    @Test
    fun aTypedEntryDoesNotComeBackAfterThePadClosed() {
        setCards(NARROW)
        press("10", "4", "5")
        crossTo(WIDE)
        closePadIfOpen()
        crossTo(NARROW)
        assertPadClosed()
        press("10")
        assertSelected("10")
    }

    @Test
    fun theTwoCardsStackJustBelowTheWidth() {
        setCards(BELOW_SIDE_BY_SIDE)
        val lessWeight = boundsOf(R.string.workout_focus_less_weight)
        val lessReps = boundsOf(R.string.workout_focus_fewer_reps)
        assertTrue("Reps should sit below Weight", lessReps.top >= lessWeight.bottom)
        assertEquals(lessWeight.left.value, lessReps.left.value, TOLERANCE)
        assertEquals(
            boundsOf(R.string.workout_focus_more_weight).right.value,
            boundsOf(R.string.workout_focus_more_reps).right.value,
            TOLERANCE,
        )
    }

    @Test
    fun theTwoCardsSitSideBySideFromTheWidth() {
        setCards(SIDE_BY_SIDE)
        val moreWeight = boundsOf(R.string.workout_focus_more_weight)
        val lessReps = boundsOf(R.string.workout_focus_fewer_reps)
        assertEquals(moreWeight.top.value, lessReps.top.value, TOLERANCE)
        assertTrue("Reps should sit right of Weight", lessReps.left >= moreWeight.right)
        // Each card takes half: the two steppers are the same width.
        assertEquals(
            (moreWeight.right - boundsOf(R.string.workout_focus_less_weight).left).value,
            (boundsOf(R.string.workout_focus_more_reps).right - lessReps.left).value,
            TOLERANCE,
        )
    }

    @Test
    fun aLoneCardTakesTheWholeWidthEvenWhenWide() {
        setCards(WIDE, trackingType = ExerciseTrackingType.DURATION)
        val fewer = boundsOf(R.string.workout_focus_fewer_seconds)
        val more = boundsOf(R.string.workout_focus_more_seconds)
        assertTrue("The stepper should span more than half the width", (more.right - fewer.left) > WIDE / 2)
    }

    private companion object {
        val NARROW = 384.dp
        val WIDE = 400.dp
        val BELOW_SIDE_BY_SIDE = 399.dp
        val SIDE_BY_SIDE = 400.dp
        const val TOLERANCE = 1f
    }
}
