package com.repflow.app.presentation.exercise.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.ExerciseValidationError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stateless Compose coverage for [ExerciseEditorScreen] - state in, events
 * out, no Hilt (see plan.md section H / L). [createAndroidComposeRule] is
 * used only for `.activity.getString(...)` access to real string resources.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        uiState: ExerciseEditorUiState,
        onNameChanged: (String) -> Unit = {},
        onTrackingTypeChanged: (ExerciseTrackingType) -> Unit = {},
        onInstructionsChanged: (String) -> Unit = {},
        onRestSecondsChanged: (String) -> Unit = {},
        onLoadIncrementChanged: (String) -> Unit = {},
        onSaveClicked: () -> Unit = {},
        onBackRequested: () -> Unit = {},
        onDiscardConfirmed: () -> Unit = {},
        onDiscardCancelled: () -> Unit = {},
        onMessageShown: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            ExerciseEditorScreen(
                uiState = uiState,
                onNameChanged = onNameChanged,
                onTrackingTypeChanged = onTrackingTypeChanged,
                onInstructionsChanged = onInstructionsChanged,
                onRestSecondsChanged = onRestSecondsChanged,
                onLoadIncrementChanged = onLoadIncrementChanged,
                onSaveClicked = onSaveClicked,
                onBackRequested = onBackRequested,
                onDiscardConfirmed = onDiscardConfirmed,
                onDiscardCancelled = onDiscardCancelled,
                onMessageShown = onMessageShown,
            )
        }
    }

    @Test
    fun rendersTheCreateTitleAndDisabledSaveWhenNameIsBlank() {
        setContent(ExerciseEditorUiState())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_title_create)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_save)).assertIsNotEnabled()
    }

    @Test
    fun rendersTheEditTitle() {
        setContent(ExerciseEditorUiState(mode = ExerciseEditorMode.Edit(ExerciseId("id-1"))))

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_title_edit)).assertIsDisplayed()
    }

    @Test
    fun rendersALoadingIndicatorWhileLoading() {
        setContent(
            ExerciseEditorUiState(
                mode = ExerciseEditorMode.Edit(ExerciseId("id-1")),
                loadStatus = ExerciseEditorLoadStatus.LOADING,
            ),
        )

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_name_label)).assertDoesNotExist()
    }

    @Test
    fun rendersTheNotFoundMessage() {
        setContent(
            ExerciseEditorUiState(
                mode = ExerciseEditorMode.Edit(ExerciseId("id-1")),
                loadStatus = ExerciseEditorLoadStatus.NOT_FOUND,
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_not_found))
            .assertIsDisplayed()
    }

    @Test
    fun nameFieldInputInvokesOnNameChanged() {
        var name: String? = null
        setContent(ExerciseEditorUiState(), onNameChanged = { name = it })

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_name_label))
            .performTextInput("Squat")

        assertEquals("Squat", name)
    }

    @Test
    fun nameFieldShowsTheBlankNameError() {
        setContent(
            ExerciseEditorUiState(
                name = "   ",
                nameError = ExerciseEditorFieldError.Domain(ExerciseValidationError.NameBlank),
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_error_name_blank))
            .assertIsDisplayed()
    }

    @Test
    fun trackingTypeChipClickInvokesOnTrackingTypeChanged() {
        var selected: ExerciseTrackingType? = null
        setContent(ExerciseEditorUiState(), onTrackingTypeChanged = { selected = it })

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_tracking_type_reps_only))
            .performClick()

        assertEquals(ExerciseTrackingType.REPS_ONLY, selected)
    }

    @Test
    fun loadIncrementFieldIsHiddenForATrackingTypeThatDoesNotSupportLoad() {
        setContent(ExerciseEditorUiState(trackingType = ExerciseTrackingType.REPS_ONLY))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_load_increment_label))
            .assertDoesNotExist()
    }

    @Test
    fun restDurationPresetClickInvokesOnRestSecondsChanged() {
        var restSeconds: String? = null
        setContent(ExerciseEditorUiState(), onRestSecondsChanged = { restSeconds = it })

        composeRule.onNodeWithText("90").performClick()

        assertEquals("90", restSeconds)
    }

    @Test
    fun saveButtonClickInvokesOnSaveClickedWhenEnabled() {
        var saved = false
        setContent(ExerciseEditorUiState(name = "Squat"), onSaveClicked = { saved = true })

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_save)).performClick()

        assertEquals(true, saved)
    }

    @Test
    fun discardDialogIsShownAndConfirmInvokesOnDiscardConfirmed() {
        var confirmed = false
        setContent(
            ExerciseEditorUiState(isDiscardDialogVisible = true),
            onDiscardConfirmed = { confirmed = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_discard_dialog_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_discard_dialog_confirm))
            .performClick()

        assertEquals(true, confirmed)
    }

    @Test
    fun backButtonClickInvokesOnBackRequested() {
        var backRequested = false
        setContent(ExerciseEditorUiState(), onBackRequested = { backRequested = true })

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.exercise_editor_back_content_description))
            .performClick()

        assertEquals(true, backRequested)
    }

    @Test
    fun submitErrorTextIsDisplayed() {
        setContent(
            ExerciseEditorUiState(
                submitError = ExerciseEditorSubmitError(ExerciseEditorSubmitErrorKind.DUPLICATE_NAME),
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_submit_error_duplicate_name))
            .assertIsDisplayed()
    }
}
