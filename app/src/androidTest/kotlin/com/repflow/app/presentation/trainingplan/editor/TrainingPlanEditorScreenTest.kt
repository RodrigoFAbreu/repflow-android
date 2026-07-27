package com.repflow.app.presentation.trainingplan.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanValidationError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stateless Compose coverage for [TrainingPlanEditorScreen], mirroring
 * [com.repflow.app.presentation.exercise.editor.ExerciseEditorScreenTest]'s
 * shape.
 */
@RunWith(AndroidJUnit4::class)
class TrainingPlanEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        uiState: TrainingPlanEditorUiState,
        onNameChanged: (String) -> Unit = {},
        rowActions: TrainingPlanEditorRowActions = noOpRowActions(),
        onAddRowClicked: () -> Unit = {},
        onSaveClicked: () -> Unit = {},
        onBackRequested: () -> Unit = {},
        onDiscardConfirmed: () -> Unit = {},
        onDiscardCancelled: () -> Unit = {},
    ) {
        composeRule.setContent {
            TrainingPlanEditorScreen(
                uiState = uiState,
                onNameChanged = onNameChanged,
                rowActions = rowActions,
                onAddRowClicked = onAddRowClicked,
                onSaveClicked = onSaveClicked,
                onBackRequested = onBackRequested,
                onDiscardConfirmed = onDiscardConfirmed,
                onDiscardCancelled = onDiscardCancelled,
            )
        }
    }

    private fun noOpRowActions() =
        TrainingPlanEditorRowActions(
            onExerciseSelected = { _, _ -> },
            onTargetSetsChanged = { _, _ -> },
            onTargetWarmupSetsChanged = { _, _ -> },
            onRepMinChanged = { _, _ -> },
            onRepMaxChanged = { _, _ -> },
            onDurationMinChanged = { _, _ -> },
            onDurationMaxChanged = { _, _ -> },
            onRestSecondsChanged = { _, _ -> },
            onOptionalChanged = { _, _ -> },
            onMoveUp = {},
            onMoveDown = {},
            onRemove = {},
        )

    @Test
    fun rendersTheCreateTitleAndDisabledSaveWhenNameIsBlank() {
        setContent(TrainingPlanEditorUiState())

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_title_create))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_save)).assertIsNotEnabled()
    }

    @Test
    fun rendersTheEditTitle() {
        setContent(TrainingPlanEditorUiState(mode = TrainingPlanEditorMode.Edit(TrainingPlanId("id-1"))))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_title_edit))
            .assertIsDisplayed()
    }

    @Test
    fun rendersALoadingIndicatorWhileLoading() {
        setContent(
            TrainingPlanEditorUiState(
                mode = TrainingPlanEditorMode.Edit(TrainingPlanId("id-1")),
                loadStatus = TrainingPlanEditorLoadStatus.LOADING,
            ),
        )

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_name_label)).assertDoesNotExist()
    }

    @Test
    fun rendersTheNotFoundMessage() {
        setContent(
            TrainingPlanEditorUiState(
                mode = TrainingPlanEditorMode.Edit(TrainingPlanId("id-1")),
                loadStatus = TrainingPlanEditorLoadStatus.NOT_FOUND,
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_not_found))
            .assertIsDisplayed()
    }

    @Test
    fun nameFieldInputInvokesOnNameChanged() {
        var name: String? = null
        setContent(TrainingPlanEditorUiState(), onNameChanged = { name = it })

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_name_label))
            .performTextInput("Push Pull Legs")

        assertEquals("Push Pull Legs", name)
    }

    @Test
    fun nameFieldShowsTheBlankNameError() {
        setContent(
            TrainingPlanEditorUiState(
                name = "   ",
                nameError = TrainingPlanEditorFieldError.Domain(TrainingPlanValidationError.NameBlank),
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_error_name_blank))
            .assertIsDisplayed()
    }

    @Test
    fun rendersTheNoExercisesHintWhenRowsAreEmpty() {
        setContent(TrainingPlanEditorUiState())

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_no_exercises))
            .assertIsDisplayed()
    }

    @Test
    fun addExerciseButtonClickInvokesOnAddRowClicked() {
        var added = false
        setContent(TrainingPlanEditorUiState(), onAddRowClicked = { added = true })

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_add_exercise))
            .performClick()

        assertEquals(true, added)
    }

    @Test
    fun removeRowActionInvokesOnRemove() {
        var removed: Long? = null
        val row = PlannedExerciseRowUiState(rowId = 42L, exerciseName = "Squat")
        setContent(
            TrainingPlanEditorUiState(rows = listOf(row)),
            rowActions = noOpRowActions().copy(onRemove = { removed = it }),
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.training_plan_editor_row_remove_content_description))
            .performClick()

        assertEquals(42L, removed)
    }

    @Test
    fun moveDownActionOnTheFirstOfTwoRowsInvokesOnMoveDown() {
        var movedDown: Long? = null
        val rows =
            listOf(
                PlannedExerciseRowUiState(rowId = 1L, exerciseName = "Squat"),
                PlannedExerciseRowUiState(rowId = 2L, exerciseName = "Row"),
            )
        setContent(
            TrainingPlanEditorUiState(rows = rows),
            rowActions = noOpRowActions().copy(onMoveDown = { movedDown = it }),
        )

        composeRule
            .onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.training_plan_editor_row_move_down_content_description),
            )[0]
            .performClick()

        assertEquals(1L, movedDown)
    }

    @Test
    fun moveUpActionOnTheSecondOfTwoRowsInvokesOnMoveUp() {
        var movedUp: Long? = null
        val rows =
            listOf(
                PlannedExerciseRowUiState(rowId = 1L, exerciseName = "Squat"),
                PlannedExerciseRowUiState(rowId = 2L, exerciseName = "Row"),
            )
        setContent(
            TrainingPlanEditorUiState(rows = rows),
            rowActions = noOpRowActions().copy(onMoveUp = { movedUp = it }),
        )

        composeRule
            .onAllNodesWithContentDescription(
                composeRule.activity.getString(R.string.training_plan_editor_row_move_up_content_description),
            )[1]
            .performClick()

        assertEquals(2L, movedUp)
    }

    @Test
    fun saveButtonClickInvokesOnSaveClickedWhenEnabled() {
        var saved = false
        val row =
            PlannedExerciseRowUiState(
                rowId = 1L,
                exerciseId = "ex-1",
                targetSetsText = "3",
                repMinText = "8",
                repMaxText = "12",
            )
        setContent(TrainingPlanEditorUiState(name = "Push Pull Legs", rows = listOf(row)), onSaveClicked = { saved = true })

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_save)).performClick()

        assertEquals(true, saved)
    }

    @Test
    fun discardDialogIsShownAndConfirmInvokesOnDiscardConfirmed() {
        var confirmed = false
        setContent(
            TrainingPlanEditorUiState(isDiscardDialogVisible = true),
            onDiscardConfirmed = { confirmed = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_discard_dialog_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_discard_dialog_confirm))
            .performClick()

        assertEquals(true, confirmed)
    }

    @Test
    fun backButtonClickInvokesOnBackRequested() {
        var backRequested = false
        setContent(TrainingPlanEditorUiState(), onBackRequested = { backRequested = true })

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.training_plan_editor_back_content_description))
            .performClick()

        assertEquals(true, backRequested)
    }

    @Test
    fun submitErrorTextIsDisplayed() {
        setContent(
            TrainingPlanEditorUiState(
                submitError = TrainingPlanEditorSubmitError(TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME),
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_submit_error_duplicate_name))
            .assertIsDisplayed()
    }
}
