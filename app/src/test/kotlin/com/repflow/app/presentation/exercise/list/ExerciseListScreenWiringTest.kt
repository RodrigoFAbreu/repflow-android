package com.repflow.app.presentation.exercise.list

import com.repflow.app.R
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.presentation.designsystem.components.RepFlowButtonDefaults
import com.repflow.app.presentation.designsystem.components.RepFlowTagDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the exercise list's own non-composable wiring - the parts of this
 * checkpoint that a device test and a screenshot both look straight past.
 *
 * `ExerciseListScreenTest` in `androidTest` already proves each state
 * *renders* and each affordance fires its callback. What it cannot see is a
 * mapping that is silently wrong in a state it does not construct: a filter
 * row whose labels and emitted enum have drifted apart (the picker is
 * index-based, so an inverted order still renders two plausible pills and
 * still fires `onFilterChanged` - with the wrong value), or two empty
 * reasons collapsed onto one message. Those are the assertions below.
 *
 * The sizes are here for the same reason CP1's token hexes are pinned: they
 * are transcriptions of the design, and nothing else would catch a typo that
 * leaves the row or the FAB looking merely plausible.
 */
class ExerciseListScreenWiringTest {
    @Test
    fun filterOrderCoversEveryStatusFilterExactlyOnce() {
        assertEquals(ExerciseStatusFilter.entries.toSet(), ExerciseListFilterOrder.toSet())
        assertEquals(ExerciseStatusFilter.entries.size, ExerciseListFilterOrder.size)
    }

    /**
     * The picker reports an index into [ExerciseListFilterOrder] and the row
     * reads its labels from the same list, so an index maps to the label the
     * user actually tapped only while both agree. Swapping the two entries
     * would leave "Archived" selecting `ACTIVE`.
     */
    @Test
    fun eachFilterCarriesItsOwnDistinctLabel() {
        val labels = ExerciseListFilterOrder.map(::exerciseListFilterLabelRes)
        assertEquals(labels.toSet().size, labels.size)
        assertEquals(R.string.exercise_list_filter_active, exerciseListFilterLabelRes(ExerciseStatusFilter.ACTIVE))
        assertEquals(R.string.exercise_list_filter_archived, exerciseListFilterLabelRes(ExerciseStatusFilter.ARCHIVED))
    }

    @Test
    fun everyEmptyReasonExplainsItselfDifferently() {
        val messages = ExerciseListEmptyReason.entries.map(::exerciseListEmptyMessageRes)
        assertEquals(messages.toSet().size, messages.size)
        assertEquals(
            R.string.exercise_list_empty_no_search_results,
            exerciseListEmptyMessageRes(ExerciseListEmptyReason.NO_SEARCH_RESULTS),
        )
    }

    /** The design's own values: rows 56-68 tall, the FAB 60x60. */
    @Test
    fun rowAndFabSizesAreTheDesignsOwn() {
        assertTrue("$ExerciseRowMinHeight outside the design's 56-68 row range", ExerciseRowMinHeight.value in 56f..68f)
        assertEquals(60f, ExerciseFabSize.value, 0f)
    }

    /**
     * Every tap target on this screen clears 44dp - the design's own layout
     * rule, and the floor CP3's primitives already hold themselves to.
     */
    @Test
    fun everyTapTargetClearsTheMinimum() {
        val minimum = RepFlowButtonDefaults.neutralOutlineMinHeight.value
        assertEquals(44f, minimum, 0f)
        assertTrue("FAB smaller than $minimum", ExerciseFabSize.value >= minimum)
        // The archived badge is deliberately *not* interactive: at the chip's
        // own 28dp it could not clear the floor, which is why the filter row
        // uses the pill picker instead of a clickable chip.
        assertTrue(RepFlowTagDefaults.minHeight.value < minimum)
    }
}
