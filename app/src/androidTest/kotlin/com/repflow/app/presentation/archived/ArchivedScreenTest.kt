package com.repflow.app.presentation.archived

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Archived screen (remediation-1-remediation-1 CP7, Q4; design turn 7 `7c`
 * N1): two sections of name, `Archived <date>` and an outlined 44dp `Restore`;
 * a restored row leaves at once; a section with nothing says so inline, and
 * with both empty one empty state replaces them; `<name> restored` carries
 * `Undo`.
 */
@RunWith(AndroidJUnit4::class)
class ArchivedScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val restored = mutableListOf<ArchivedItem>()
    private val undone = mutableListOf<ArchivedTarget>()
    private val shown = mutableListOf<Long>()
    private var retries = 0

    private val squat =
        ArchivedItem(ArchivedTarget.Exercise(ExerciseId("e-squat")), "Smith Machine Squat", Instant.parse("2026-09-12T10:00:00Z"))
    private val fly =
        ArchivedItem(ArchivedTarget.Exercise(ExerciseId("e-fly")), "Cable Fly", Instant.parse("2026-08-03T10:00:00Z"))
    private val oldPlan =
        ArchivedItem(ArchivedTarget.Plan(TrainingPlanId("p-old")), "Old Plan", Instant.parse("2026-02-02T10:00:00Z"))

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun render(initial: ArchivedUiState) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(initial) }
            RepFlowTheme {
                ArchivedScreen(
                    uiState = uiState,
                    onRestoreClicked = { item ->
                        restored += item
                        val loaded = uiState.content as ArchivedContent.Loaded
                        uiState =
                            uiState.copy(
                                content =
                                    loaded.copy(
                                        exercises = loaded.exercises - item,
                                        plans = loaded.plans - item,
                                    ),
                            )
                    },
                    onUndoRestoreClicked = { undone += it },
                    onMessageShown = { id ->
                        shown += id
                        uiState = uiState.copy(messages = uiState.messages.filterNot { it.id == id })
                    },
                    onRetry = { retries++ },
                    onBack = {},
                )
            }
        }
    }

    private fun loaded(
        exercises: List<ArchivedItem> = emptyList(),
        plans: List<ArchivedItem> = emptyList(),
        messages: List<ArchivedMessage> = emptyList(),
    ) = ArchivedUiState(ArchivedContent.Loaded(exercises, plans), messages)

    @Test
    fun rowsShowTheNameTheArchiveDateAndAnOutlinedRestoreOfAtLeast44dp() {
        render(loaded(exercises = listOf(squat, fly), plans = listOf(oldPlan)))

        composeRule.onNodeWithText(string(R.string.archived_section_exercises).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.archived_section_plans).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText("Smith Machine Squat").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.archived_row_meta, "12 Sep 2026")).assertIsDisplayed()
        composeRule.onNodeWithText("Old Plan").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(string(R.string.archived_restore_content_description, "Smith Machine Squat"))
            .assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun restoringARowReportsItAndTheRowLeavesAtOnce() {
        render(loaded(exercises = listOf(squat, fly)))

        composeRule
            .onNodeWithContentDescription(string(R.string.archived_restore_content_description, "Cable Fly"))
            .performClick()

        assertEquals(listOf(fly), restored)
        composeRule.onNodeWithText("Cable Fly").assertDoesNotExist()
        composeRule.onNodeWithText("Smith Machine Squat").assertIsDisplayed()
    }

    @Test
    fun anEmptySectionSaysSoInlineBesideTheOneThatHasRows() {
        render(loaded(exercises = listOf(squat)))

        composeRule.onNodeWithText("Smith Machine Squat").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.archived_empty_plans)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.archived_empty_exercises)).assertDoesNotExist()
    }

    @Test
    fun withBothSectionsEmptyOneEmptyStateReplacesThem() {
        render(loaded())

        composeRule.onNodeWithText(string(R.string.archived_empty_both)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.archived_section_exercises).uppercase()).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.archived_empty_plans)).assertDoesNotExist()
    }

    @Test
    fun aRestoredMessageNamesTheRowAndUndoReportsItsTarget() {
        val message = ArchivedMessage.Restored(id = 1, target = fly.target, name = "Cable Fly")
        render(loaded(exercises = listOf(squat), messages = listOf(message)))

        composeRule.onNodeWithText(string(R.string.archived_message_restored, "Cable Fly")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.archived_message_restored_undo)).performClick()

        composeRule.waitUntil(WAIT_MILLIS) { shown == listOf(1L) }
        assertEquals(listOf(fly.target), undone)
    }

    @Test
    fun anObservationFailureOffersARetry() {
        render(ArchivedUiState(ArchivedContent.ObservationFailed))

        composeRule.onNodeWithText(string(R.string.archived_observation_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.archived_retry)).performClick()

        assertEquals(1, retries)
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
    }
}
