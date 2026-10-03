package com.repflow.app.presentation.workout

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Functional review J3 (`D56`): the exercise created from the workout picker is found in the picker's list by id. */
class CreatedExerciseTest {
    private val squat = ExercisePickerItem(ExerciseId("squat"), "Squat", ExerciseTrackingType.WEIGHT_AND_REPS)
    private val plank = ExercisePickerItem(ExerciseId("plank"), "Plank", ExerciseTrackingType.DURATION)

    @Test
    fun `the created exercise is the list item with that id`() {
        assertEquals(plank, createdExercisePickerItem("plank", listOf(squat, plank)))
    }

    @Test
    fun `nothing is added without an id or while the list does not hold it yet`() {
        assertNull(createdExercisePickerItem(null, listOf(squat, plank)))
        assertNull(createdExercisePickerItem("plank", listOf(squat)))
    }
}
