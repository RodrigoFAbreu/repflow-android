package com.repflow.app.presentation.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Functional review J3 (`D56`): the exercise the editor hands back is added to
 * the running workout once - as soon as the picker's list holds it - and the
 * hand-back is then cleared.
 */
@RunWith(AndroidJUnit4::class)
class CreatedExerciseEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val curl = ExercisePickerItem(ExerciseId("curl"), "Curl", ExerciseTrackingType.WEIGHT_AND_REPS)

    @Test
    fun addsTheCreatedExerciseOnceItAppearsInTheListAndThenReportsItHandled() {
        var available by mutableStateOf(emptyList<ExercisePickerItem>())
        var createdId by mutableStateOf<String?>("curl")
        val added = mutableListOf<ExercisePickerItem>()
        var handled = 0
        composeRule.setContent {
            CreatedExerciseEffect(
                createdExerciseId = createdId,
                availableExercises = available,
                onAdd = { added += it },
                onHandled = {
                    handled++
                    createdId = null
                },
            )
        }

        composeRule.waitForIdle()
        assertEquals(emptyList<ExercisePickerItem>(), added)
        assertEquals(0, handled)

        available = listOf(curl)
        composeRule.waitForIdle()

        assertEquals(listOf(curl), added)
        assertEquals(1, handled)
    }
}
