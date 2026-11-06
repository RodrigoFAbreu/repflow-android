package com.repflow.app.presentation.trainingplan.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
        onNameFocusLost: () -> Unit = {},
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
                    onNameFocusLost = onNameFocusLost,
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

    /** Functional review R2-F-1: tapping the name field and leaving it counts as touching it. */
    @Test
    fun leavingTheFocusedNameFieldReportsFocusLost() {
        var focusLost = 0
        lateinit var focusManager: FocusManager
        composeRule.setContent {
            RepFlowTheme {
                focusManager = LocalFocusManager.current
                TrainingPlanEditorScreen(
                    uiState = TrainingPlanEditorUiState(),
                    onNameChanged = {},
                    onNameFocusLost = { focusLost++ },
                    rowActions = noOpRowActions(),
                    onAddRowClicked = {},
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onCreateExerciseClick = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, focusLost)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_name_label)).performClick()
        composeRule.waitForIdle()
        assertEquals(0, focusLost)

        composeRule.runOnIdle { focusManager.clearFocus() }
        composeRule.waitForIdle()
        assertEquals(1, focusLost)
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

    /**
     * Functional review R3-F-4: the name field keeps focus (and so the keyboard)
     * while its error appears and disappears; no keystroke is lost.
     */
    @Test
    fun theNameFieldKeepsFocusWhileItsErrorAppearsAndDisappears() {
        var uiState by mutableStateOf(TrainingPlanEditorUiState(name = "Legs A", nameTouched = true))
        composeRule.setContent {
            RepFlowTheme {
                TrainingPlanEditorScreen(
                    uiState = uiState,
                    onNameChanged = { value ->
                        uiState =
                            uiState.copy(
                                name = value,
                                nameTouched = true,
                                nameError =
                                    if (value.isBlank()) {
                                        TrainingPlanEditorFieldError.Domain(TrainingPlanValidationError.NameBlank)
                                    } else {
                                        null
                                    },
                            )
                    },
                    onNameFocusLost = {},
                    rowActions = noOpRowActions(),
                    onAddRowClicked = {},
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onCreateExerciseClick = {},
                )
            }
        }
        val name = composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_name_label))
        val blankError = composeRule.activity.getString(R.string.training_plan_editor_error_name_blank)

        name.performClick()
        name.assertIsFocused()
        name.performTextReplacement("")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(blankError).assertIsDisplayed()
        name.assertIsFocused()

        name.performTextInput("L")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(blankError).assertDoesNotExist()
        name.assertIsFocused()
        name.performTextInput("e")
        assertEquals("Le", uiState.name)
        name.assertIsFocused()
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

    private fun longPlan(submitError: TrainingPlanEditorSubmitError? = null) =
        TrainingPlanEditorUiState(
            name = "Push",
            rows =
                (1L..12L).map {
                    PlannedExerciseRowUiState(
                        rowId = it,
                        exerciseId = "exercise-$it",
                        exerciseName = "Exercise $it",
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        targetSetsText = "3",
                        repMinText = "8",
                        repMaxText = "12",
                    )
                },
            submitError = submitError,
        )

    /** Functional review R2-F-2: the reason is beside the name, not at the foot of a long list. */
    @Test
    fun aDuplicateNameErrorShowsNextToTheNameEvenWithALongList() {
        setContent(longPlan(TrainingPlanEditorSubmitError(TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME)))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_submit_error_duplicate_name))
            .assertIsDisplayed()
    }

    /** Functional review R2-F-2: a refused save while scrolled down scrolls the name back into view. */
    @Test
    fun aDuplicateNameErrorScrollsTheNameIntoView() {
        var uiState by mutableStateOf(longPlan())
        composeRule.setContent {
            RepFlowTheme {
                TrainingPlanEditorScreen(
                    uiState = uiState,
                    onNameChanged = {},
                    onNameFocusLost = {},
                    rowActions = noOpRowActions(),
                    onAddRowClicked = {},
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onCreateExerciseClick = {},
                )
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_add_exercise)).performScrollTo()

        uiState = longPlan(TrainingPlanEditorSubmitError(TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME))
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_submit_error_duplicate_name))
            .assertIsDisplayed()
    }

    /** Functional review R3-F-2: a tap outside the name field and the keyboard's Done take focus off it. */
    @Test
    fun tappingOutsideTheNameFieldAndDoneClearFocusButTappingTheFieldDoesNot() {
        setContent(TrainingPlanEditorUiState(name = "Legs A"))
        val name = composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_name_label))

        name.performClick()
        name.assertIsFocused()
        name.performClick()
        name.assertIsFocused()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_no_exercises)).performClick()
        name.assertIsNotFocused()

        name.performClick()
        name.assertIsFocused()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_add_exercise)).performClick()
        name.assertIsNotFocused()

        name.performClick()
        name.performImeAction()
        name.assertIsNotFocused()
    }

    /** Functional review R3-F-3: a second refused save for the same reason brings the name into view again. */
    @Test
    fun aRepeatedDuplicateNameRefusalScrollsTheNameIntoViewAgain() {
        var uiState by mutableStateOf(longPlan())
        composeRule.setContent {
            RepFlowTheme {
                TrainingPlanEditorScreen(
                    uiState = uiState,
                    onNameChanged = {},
                    onNameFocusLost = {},
                    rowActions = noOpRowActions(),
                    onAddRowClicked = {},
                    onSaveClicked = {},
                    onBackRequested = {},
                    onDiscardConfirmed = {},
                    onDiscardCancelled = {},
                    onCreateExerciseClick = {},
                )
            }
        }
        val duplicate = composeRule.activity.getString(R.string.training_plan_editor_submit_error_duplicate_name)

        repeat(3) { attempt ->
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_add_exercise)).performScrollTo()
            uiState =
                longPlan(TrainingPlanEditorSubmitError(TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME, attempt + 1))
            composeRule.waitForIdle()
            composeRule.onNodeWithText(duplicate).assertIsDisplayed()
        }
    }

    /** Functional review R2-F-4: a blank field on a new row asks for a value; it never says to add an exercise. */
    @Test
    fun aBlankRowFieldAsksForAValueNotForAnExercise() {
        val blank = TrainingPlanEditorFieldError.Required
        setContent(
            TrainingPlanEditorUiState(
                rows =
                    listOf(
                        PlannedExerciseRowUiState(
                            rowId = 1L,
                            exerciseId = "exercise-1",
                            exerciseName = "Bench Press",
                            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                            targetSetsError = blank,
                            targetRangeError = blank,
                        ),
                    ),
            ),
        )

        composeRule.onNodeWithText("Bench Press").performClick()

        composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.training_plan_editor_error_required)).assertCountEquals(2)
        composeRule.onNodeWithText("Add at least one exercise.").assertDoesNotExist()
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
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_editor_picker_search_hint))
            .assertHeightIsEqualTo(48.dp)
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
                    onNameFocusLost = {},
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

    // ---- The open row's pads (timed-entry-mmss CP2) ----

    /** What each stepper line reported, in order, as `line=text`. */
    private val reported = mutableListOf<String>()

    /**
     * Renders one row and opens it, feeding each reported value back as the
     * ViewModel does. `Working sets` is 4 and the warm-ups blank, so no key a
     * test presses shares its text with a stepper's value.
     */
    private fun openTimedRow(
        min: String = "30",
        max: String = "60",
        rest: String = "45",
        fontScale: Float = 1f,
    ) {
        var row by mutableStateOf(
            PlannedExerciseRowUiState(
                rowId = 1L,
                exerciseId = "ex-plank",
                exerciseName = "Plank",
                trackingType = ExerciseTrackingType.DURATION,
                targetSetsText = "4",
                targetWarmupSetsText = "",
                durationMinText = min,
                durationMaxText = max,
                restSecondsText = rest,
            ),
        )
        val actions =
            noOpRowActions().copy(
                onTargetSetsChanged = { _, value ->
                    reported += "sets=$value"
                    row = row.copy(targetSetsText = value)
                },
                onTargetWarmupSetsChanged = { _, value ->
                    reported += "warmups=$value"
                    row = row.copy(targetWarmupSetsText = value)
                },
                onDurationMinChanged = { _, value ->
                    reported += "min=$value"
                    row = row.copy(durationMinText = value)
                },
                onDurationMaxChanged = { _, value ->
                    reported += "max=$value"
                    row = row.copy(durationMaxText = value)
                },
                onRestSecondsChanged = { _, value ->
                    reported += "rest=$value"
                    row = row.copy(restSecondsText = value)
                },
            )
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                RepFlowTheme {
                    TrainingPlanEditorScreen(
                        uiState = TrainingPlanEditorUiState(rows = listOf(row)),
                        onNameChanged = {},
                        onNameFocusLost = {},
                        rowActions = actions,
                        onAddRowClicked = {},
                        onSaveClicked = {},
                        onBackRequested = {},
                        onDiscardConfirmed = {},
                        onDiscardCancelled = {},
                        onCreateExerciseClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Plank").performClick()
    }

    /** The stepper's value, not a rest preset that happens to read the same. */
    private fun lineValue(text: String) = composeRule.onNode(hasText(text) and hasClickAction() and !isSelectable())

    /** The pad's selected value, not a selected rest preset that happens to read the same. */
    private fun padValueSelected(text: String) = composeRule.onNode(hasText(text) and isSelected() and !hasClickAction())

    private fun pressKeys(vararg keys: String) = keys.forEach { key -> composeRule.onNodeWithText(key).performClick() }

    private fun pressSet() = composeRule.onNodeWithText(composeRule.activity.getString(R.string.repflow_keypad_confirm)).performClick()

    @Test
    fun aTimedRowsLinesAreLabelledInTime() {
        openTimedRow()

        composeRule.onNodeWithText("Min time").assertIsDisplayed()
        composeRule.onNodeWithText("Max time").assertIsDisplayed()
        composeRule.onNodeWithText("Min seconds").assertDoesNotExist()
        composeRule.onNodeWithText("Max seconds").assertDoesNotExist()
    }

    @Test
    fun minTimeOpensOnItsClockAndTakesMinutesAndSeconds() {
        openTimedRow()

        lineValue("0:30").performScrollTo().performClick()
        padValueSelected("0:30").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.repflow_keypad_duration_caption)).assertIsDisplayed()
        pressKeys("1", "3", "0")
        pressSet()

        assertEquals(listOf("min=90"), reported)
        lineValue("1:30").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun maxTimeOpensOnItsClockAndTakesMinutesAndSeconds() {
        openTimedRow()

        lineValue("1:00").performScrollTo().performClick()
        padValueSelected("1:00").assertIsDisplayed()
        pressKeys("1", "3", "0")
        pressSet()

        assertEquals(listOf("max=90"), reported)
        lineValue("1:30").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun setUnchangedOnAPlanTimeReportsTheSameSeconds() {
        openTimedRow()

        lineValue("0:30").performScrollTo().performClick()
        pressSet()

        assertEquals(listOf("min=30"), reported)
        lineValue("0:30").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aTypedTimeAboveThePlanRangeClampsToItsTop() {
        openTimedRow()

        lineValue("1:00").performScrollTo().performClick()
        pressKeys("1", "2", "5", "0", "0")
        pressSet()

        assertEquals(listOf("max=7200"), reported)
        lineValue("120:00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theRestLineOpensOnItsClockTitledRestAndTakesMinutesAndSeconds() {
        openTimedRow(rest = "90")

        lineValue("1:30").performScrollTo().performClick()
        padValueSelected("1:30").assertIsDisplayed()
        // The line's own label and the pad's title.
        composeRule.onAllNodesWithText("Rest").assertCountEquals(2)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.repflow_keypad_duration_caption)).assertIsDisplayed()
        pressKeys("1", "2", "5")
        pressSet()

        assertEquals(listOf("rest=85"), reported)
        lineValue("1:25").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theRestLineTakes130AsNinetySeconds() {
        openTimedRow()

        lineValue("0:45").performScrollTo().performClick()
        pressKeys("1", "3", "0")
        pressSet()

        assertEquals(listOf("rest=90"), reported)
    }

    @Test
    fun workingSetsOpensOnItsValueWithTheWholeNumberPad() {
        openTimedRow()

        lineValue("4").performScrollTo().performClick()
        padValueSelected("4").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.repflow_keypad_whole_numbers_caption)).assertIsDisplayed()
        pressSet()

        assertEquals(listOf("sets=4"), reported)
    }

    @Test
    fun aBlankLineOpensEmpty() {
        openTimedRow()

        lineValue("–").performScrollTo().performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.repflow_keypad_whole_numbers_caption)).assertIsDisplayed()
        composeRule.onNode(hasText("0") and isSelected()).assertDoesNotExist()
        pressSet()

        assertEquals(emptyList<String>(), reported)
    }

    @Test
    fun theLongestTimeStaysOnOneLineAtFontScale2() {
        openTimedRow(max = "7200", fontScale = 2f)

        val results = mutableListOf<TextLayoutResult>()
        val value = composeRule.onNodeWithText("120:00", useUnmergedTree = true).performScrollTo()
        value.assertIsDisplayed()
        value
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        assertEquals(1, results.single().lineCount)
    }

    @Test
    fun theLineStepperIsStill164WideAtFontScale1() {
        openTimedRow()

        // The minus button's left edge to the plus button's right edge, on `Min time`'s line.
        val minus =
            composeRule
                .onNodeWithContentDescription(composeRule.activity.getString(R.string.training_plan_editor_minimum_decrease))
                .performScrollTo()
                .getUnclippedBoundsInRoot()
        val plus =
            composeRule
                .onNodeWithContentDescription(composeRule.activity.getString(R.string.training_plan_editor_minimum_increase))
                .getUnclippedBoundsInRoot()
        assertEquals(164f, (plus.right - minus.left).value, 1f)
    }

    // ---- The collapsed row's meta reads as clocks (timed-entry-mmss CP3) ----

    private fun showCollapsedTimedRow(
        min: String,
        max: String,
        rest: String,
    ) = setContent(
        TrainingPlanEditorUiState(
            rows =
                listOf(
                    PlannedExerciseRowUiState(
                        rowId = 1L,
                        exerciseId = "ex-plank",
                        exerciseName = "Plank",
                        trackingType = ExerciseTrackingType.DURATION,
                        targetSetsText = "3",
                        durationMinText = min,
                        durationMaxText = max,
                        restSecondsText = rest,
                    ),
                ),
        ),
    )

    @Test
    fun theRowMetaReadsAsClocksWithARestOverAMinute() {
        showCollapsedTimedRow(min = "30", max = "60", rest = "90")

        composeRule.onNodeWithText("3 × 0:30–1:00 · 1:30 rest").assertIsDisplayed()
    }

    @Test
    fun theRowMetaReadsAsClocksWithARestUnderAMinute() {
        showCollapsedTimedRow(min = "45", max = "900", rest = "45")

        composeRule.onNodeWithText("3 × 0:45–15:00 · 0:45 rest").assertIsDisplayed()
    }

    @Test
    fun aBlankFieldKeepsTheRowMetasPlaceholder() {
        showCollapsedTimedRow(min = "", max = "60", rest = "")

        composeRule.onNodeWithText("3 × ––1:00").assertIsDisplayed()
    }

    @Test
    fun unparsableTextShowsInTheRowMetaAsItWas() {
        showCollapsedTimedRow(min = "abc", max = "-5", rest = "1.5")

        composeRule.onNodeWithText("3 × abc–-5 · 1.5 rest").assertIsDisplayed()
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
