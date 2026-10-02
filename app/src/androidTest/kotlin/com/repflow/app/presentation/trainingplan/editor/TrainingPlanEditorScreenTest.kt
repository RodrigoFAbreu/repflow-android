package com.repflow.app.presentation.trainingplan.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanValidationError
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stateless Compose coverage for [TrainingPlanEditorScreen], mirroring
 * [com.repflow.app.presentation.exercise.editor.ExerciseEditorScreenTest]'s
 * shape. Remediation-1 CP11 replaced the row's exercise dropdown with the
 * picker sheet (the last two tests); the row actions stay on the collapsed
 * row, so the move/remove tests reach them without expanding anything.
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
        onCreateExerciseClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                TrainingPlanEditorScreen(
                    uiState = uiState,
                    onNameChanged = onNameChanged,
                    rowActions = rowActions,
                    onAddRowClicked = onAddRowClicked,
                    onSaveClicked = onSaveClicked,
                    onBackRequested = onBackRequested,
                    onDiscardConfirmed = onDiscardConfirmed,
                    onDiscardCancelled = onDiscardCancelled,
                    onCreateExerciseClick = onCreateExerciseClick,
                )
            }
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
                nameTouched = true,
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

    @Test
    fun aRowWithNoExerciseOpensThePickerSheetAndTheChoiceFillsThatRow() {
        var selected: Pair<Long, String>? = null
        val row = PlannedExerciseRowUiState(rowId = 7L)
        setContent(
            TrainingPlanEditorUiState(rows = listOf(row), availableExercises = pickerOptions()),
            rowActions = noOpRowActions().copy(onExerciseSelected = { rowId, exerciseId -> selected = rowId to exerciseId }),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_select_exercise_placeholder))
            .performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_picker_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Romanian Deadlift").performClick()

        assertEquals(7L to "ex-rdl", selected)
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_picker_title))
            .assertDoesNotExist()
    }

    @Test
    fun aRowsWarmupCountAgreesInNumber() {
        fun row(
            id: Long,
            warmups: String,
        ) = PlannedExerciseRowUiState(
            rowId = id,
            exerciseId = "ex-$id",
            exerciseName = "Exercise $id",
            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
            targetSetsText = "3",
            repMinText = "8",
            repMaxText = "12",
            targetWarmupSetsText = warmups,
        )
        setContent(TrainingPlanEditorUiState(rows = listOf(row(1L, "1"), row(2L, "2"))))

        composeRule.onNode(hasText("1 warm-up", substring = true) and !hasText("warm-ups", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasText("2 warm-ups", substring = true)).assertIsDisplayed()
    }

    @Test
    fun dismissingThePickerWithoutAChoiceRemovesTheBlankRow() {
        var removed: Long? = null
        val row = PlannedExerciseRowUiState(rowId = 7L)
        setContent(
            TrainingPlanEditorUiState(rows = listOf(row), availableExercises = pickerOptions()),
            rowActions = noOpRowActions().copy(onRemove = { removed = it }),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_select_exercise_placeholder))
            .performClick()
        // The sheet is its own window, so the key event goes through Espresso rather than the activity's dispatcher.
        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(7L, removed)
    }

    @Test
    fun creatingANewExerciseFromThePickerRemovesTheBlankRow() {
        var removed: Long? = null
        var created = false
        val row = PlannedExerciseRowUiState(rowId = 7L)
        setContent(
            TrainingPlanEditorUiState(rows = listOf(row), availableExercises = pickerOptions()),
            rowActions = noOpRowActions().copy(onRemove = { removed = it }),
            onCreateExerciseClick = { created = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_select_exercise_placeholder))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_picker_create)).performClick()

        assertEquals(7L, removed)
        assertEquals(true, created)
    }

    @Test
    fun addExerciseOpensThePickerSheetForTheNewRowOnceItAppears() {
        var state by mutableStateOf(TrainingPlanEditorUiState(availableExercises = pickerOptions()))
        var selected: Pair<Long, String>? = null
        // A stateful host: the ViewModel's new empty row has to actually appear for the sheet to open for it.
        composeRule.setContent {
            RepFlowTheme {
                TrainingPlanEditorScreen(
                    uiState = state,
                    onNameChanged = {},
                    rowActions = noOpRowActions().copy(onExerciseSelected = { rowId, exerciseId -> selected = rowId to exerciseId }),
                    onAddRowClicked = { state = state.copy(rows = state.rows + PlannedExerciseRowUiState(rowId = 3L)) },
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onCreateExerciseClick = {},
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_add_exercise))
            .performClick()
        composeRule.onNodeWithText("Plank").performClick()

        assertEquals(3L to "ex-plank", selected)
    }

    private fun pickerOptions() =
        listOf(
            TrainingPlanEditorExerciseOption(
                id = "ex-rdl",
                name = "Romanian Deadlift",
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
            ),
            TrainingPlanEditorExerciseOption(id = "ex-plank", name = "Plank", trackingType = ExerciseTrackingType.DURATION),
        )
}
