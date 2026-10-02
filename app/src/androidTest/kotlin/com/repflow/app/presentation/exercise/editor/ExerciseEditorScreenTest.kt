package com.repflow.app.presentation.exercise.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.ExerciseValidationError
import com.repflow.app.presentation.RepFlowTheme
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
        onNameFocusLost: () -> Unit = {},
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
            RepFlowTheme {
                ExerciseEditorScreen(
                    uiState = uiState,
                    onNameChanged = onNameChanged,
                    onNameFocusLost = onNameFocusLost,
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

    /** Functional review R2-F-1: tapping the name field and leaving it counts as touching it. */
    @Test
    fun leavingTheFocusedNameFieldReportsFocusLost() {
        var focusLost = 0
        lateinit var focusManager: FocusManager
        composeRule.setContent {
            RepFlowTheme {
                focusManager = LocalFocusManager.current
                ExerciseEditorScreen(
                    uiState = ExerciseEditorUiState(),
                    onNameChanged = {},
                    onNameFocusLost = { focusLost++ },
                    onTrackingTypeChanged = {},
                    onInstructionsChanged = {},
                    onRestSecondsChanged = {},
                    onLoadIncrementChanged = {},
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onMessageShown = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, focusLost)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_name_label)).performClick()
        composeRule.waitForIdle()
        assertEquals(0, focusLost)

        composeRule.runOnIdle { focusManager.clearFocus() }
        composeRule.waitForIdle()
        assertEquals(1, focusLost)
    }

    @Test
    fun nameFieldShowsTheBlankNameError() {
        setContent(
            ExerciseEditorUiState(
                name = "   ",
                nameTouched = true,
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

    /**
     * Since remediation-1 CP10 the load increment is `2b`'s `Load step` preset
     * row. The row is first shown to exist for a type that carries load, so
     * the absence asserted after the switch is the row being hidden - not a
     * label that no longer exists anywhere passing by default.
     */
    @Test
    fun loadIncrementFieldIsHiddenForATrackingTypeThatDoesNotSupportLoad() {
        var uiState by mutableStateOf(ExerciseEditorUiState(trackingType = ExerciseTrackingType.WEIGHT_AND_REPS))
        composeRule.setContent {
            RepFlowTheme {
                ExerciseEditorScreen(
                    uiState = uiState,
                    onNameChanged = {},
                    onNameFocusLost = {},
                    onTrackingTypeChanged = {},
                    onInstructionsChanged = {},
                    onRestSecondsChanged = {},
                    onLoadIncrementChanged = {},
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onMessageShown = {},
                )
            }
        }
        val loadStepLabel = composeRule.activity.getString(R.string.exercise_editor_load_step_label)
        val loadStepPreset = composeRule.activity.getString(R.string.exercise_editor_load_step_preset, "2.5")
        composeRule.onNodeWithText(loadStepLabel).assertIsDisplayed()
        composeRule.onNodeWithText(loadStepPreset).assertIsDisplayed()

        uiState = ExerciseEditorUiState(trackingType = ExerciseTrackingType.REPS_ONLY)

        composeRule.onNodeWithText(loadStepLabel).assertDoesNotExist()
        composeRule.onNodeWithText(loadStepPreset).assertDoesNotExist()
    }

    /** `2b` labels the rest presets as a clock (`1:30`); the value written is still seconds. */
    @Test
    fun restDurationPresetClickInvokesOnRestSecondsChanged() {
        var restSeconds: String? = null
        setContent(ExerciseEditorUiState(), onRestSecondsChanged = { restSeconds = it })

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_rest_clock, 1, 30)).performClick()

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

    /** Functional review R2-F-2: a refused save while scrolled down brings the name error back into view. */
    @Test
    fun aDuplicateNameErrorScrollsTheNameIntoView() {
        var uiState by mutableStateOf(ExerciseEditorUiState(name = "Squat"))
        composeRule.setContent {
            RepFlowTheme {
                // A short viewport, so the form really scrolls and the name can leave the screen.
                Box(Modifier.height(260.dp)) {
                    ExerciseEditorScreen(
                        uiState = uiState,
                        onNameChanged = {},
                        onNameFocusLost = {},
                        onTrackingTypeChanged = {},
                        onInstructionsChanged = {},
                        onRestSecondsChanged = {},
                        onLoadIncrementChanged = {},
                        onSaveClicked = {},
                        onBackRequested = {},
                        onDiscardConfirmed = {},
                        onDiscardCancelled = {},
                        onMessageShown = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_instructions_label)).performScrollTo()

        uiState = uiState.copy(submitError = ExerciseEditorSubmitError(ExerciseEditorSubmitErrorKind.DUPLICATE_NAME))
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_submit_error_duplicate_name))
            .assertIsDisplayed()
    }

    /**
     * Functional review R3-F-4: the name field keeps focus (and so the keyboard)
     * while its error appears and disappears; no keystroke is lost.
     */
    @Test
    fun theNameFieldKeepsFocusWhileItsErrorAppearsAndDisappears() {
        var uiState by mutableStateOf(ExerciseEditorUiState(name = "Plank", nameTouched = true))
        composeRule.setContent {
            RepFlowTheme {
                ExerciseEditorScreen(
                    uiState = uiState,
                    onNameChanged = { value ->
                        uiState =
                            uiState.copy(
                                name = value,
                                nameTouched = true,
                                nameError =
                                    if (value.isBlank()) {
                                        ExerciseEditorFieldError.Domain(ExerciseValidationError.NameBlank)
                                    } else {
                                        null
                                    },
                            )
                    },
                    onNameFocusLost = {},
                    onTrackingTypeChanged = {},
                    onInstructionsChanged = {},
                    onRestSecondsChanged = {},
                    onLoadIncrementChanged = {},
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onMessageShown = {},
                )
            }
        }
        val name = composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_name_label))
        val blankError = composeRule.activity.getString(R.string.exercise_editor_error_name_blank)

        name.performClick()
        name.assertIsFocused()
        name.performTextReplacement("")
        composeRule.waitForIdle()
        name.assertIsFocused()
        composeRule.onNodeWithText(blankError).assertIsDisplayed()

        name.performTextInput("P")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(blankError).assertDoesNotExist()
        name.assertIsFocused()
        name.performTextInput("l")
        assertEquals("Pl", uiState.name)
        name.assertIsFocused()
    }

    /** Functional review R3-F-3: a second refused save for the same reason brings the name into view again. */
    @Test
    fun aRepeatedDuplicateNameRefusalScrollsTheNameIntoViewAgain() {
        var uiState by mutableStateOf(ExerciseEditorUiState(name = "Squat"))
        composeRule.setContent {
            RepFlowTheme {
                Box(Modifier.height(260.dp)) {
                    ExerciseEditorScreen(
                        uiState = uiState,
                        onNameChanged = {},
                        onNameFocusLost = {},
                        onTrackingTypeChanged = {},
                        onInstructionsChanged = {},
                        onRestSecondsChanged = {},
                        onLoadIncrementChanged = {},
                        onSaveClicked = {},
                        onBackRequested = {},
                        onDiscardConfirmed = {},
                        onDiscardCancelled = {},
                        onMessageShown = {},
                    )
                }
            }
        }
        val instructions = composeRule.activity.getString(R.string.exercise_editor_instructions_label)
        val duplicate = composeRule.activity.getString(R.string.exercise_editor_submit_error_duplicate_name)

        repeat(3) { attempt ->
            composeRule.onNodeWithText(instructions).performScrollTo()
            uiState = uiState.copy(submitError = ExerciseEditorSubmitError(ExerciseEditorSubmitErrorKind.DUPLICATE_NAME, attempt + 1))
            composeRule.waitForIdle()
            composeRule.onNodeWithText(duplicate).assertIsDisplayed()
        }
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

    /** Hosts the screen over real state, so typed text round-trips the way the ViewModel would echo it. */
    private fun setStatefulContent(initial: ExerciseEditorUiState): () -> ExerciseEditorUiState {
        var uiState by mutableStateOf(initial)
        composeRule.setContent {
            RepFlowTheme {
                ExerciseEditorScreen(
                    uiState = uiState,
                    onNameChanged = {},
                    onNameFocusLost = {},
                    onTrackingTypeChanged = {},
                    onInstructionsChanged = {},
                    onRestSecondsChanged = { uiState = uiState.copy(restSecondsText = it) },
                    onLoadIncrementChanged = { uiState = uiState.copy(loadIncrementKgText = it) },
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onMessageShown = {},
                )
            }
        }
        return { uiState }
    }

    /**
     * Self-review regression: `Other` used to close the moment the typed text
     * matched a preset, so `600` could not be typed (`60` is `1:00`). It now
     * stays open while typing, and a preset tap from it selects that preset
     * rather than clearing the value.
     */
    @Test
    fun otherRestFieldStaysOpenWhileTypingThroughAPresetValue() {
        val state = setStatefulContent(ExerciseEditorUiState(trackingType = ExerciseTrackingType.REPS_ONLY))
        val restLabel = composeRule.activity.getString(R.string.exercise_editor_rest_duration_label)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_editor_preset_other)).performClick()
        composeRule.onNodeWithText(restLabel).performTextInput("6")
        composeRule.onNodeWithText(restLabel).performTextInput("0")
        composeRule.onNodeWithText(restLabel).assertIsDisplayed()
        composeRule.onNodeWithText(restLabel).performTextInput("0")

        assertEquals("600", state().restSecondsText)

        composeRule.onNodeWithText(restLabel).performTextReplacement("60")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.exercise_rest_clock, 1, 0)).performClick()

        assertEquals("60", state().restSecondsText)
        composeRule.onNodeWithText(restLabel).assertDoesNotExist()
    }

    /** The same regression on `Load step`: `5.5` passes through the `5 kg` preset. */
    @Test
    fun otherLoadStepFieldStaysOpenWhileTypingThroughAPresetValue() {
        val state = setStatefulContent(ExerciseEditorUiState(trackingType = ExerciseTrackingType.WEIGHT_AND_REPS))
        val loadLabel = composeRule.activity.getString(R.string.exercise_editor_load_increment_label)
        val other = composeRule.activity.getString(R.string.exercise_editor_preset_other)

        // The rest row's `Other` comes first, the load step's second.
        composeRule.onAllNodesWithText(other)[1].performScrollTo().performClick()
        composeRule.onNodeWithText(loadLabel).performScrollTo().performTextInput("5")
        composeRule.onNodeWithText(loadLabel).assertExists()
        composeRule.onNodeWithText(loadLabel).performTextInput(".5")

        assertEquals("5.5", state().loadIncrementKgText)
    }
}
