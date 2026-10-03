package com.repflow.app.presentation.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.ReadinessScore
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Home's screen (remediation-1 CP5), stateless: each affordance reaches its
 * callback, the abandon confirmation stands between the trash and the use
 * case, and the start and readiness sheets open from their cards.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.parse("2026-08-11")
    private val push = HomePlanOption(TrainingPlanVersionId("push-v1"), "Push day", exerciseCount = 5, workingSetCount = 19)
    private val legs = HomePlanOption(TrainingPlanVersionId("legs-v1"), "Leg day", exerciseCount = 4, workingSetCount = 14)

    private val abandoned = mutableListOf<WorkoutSessionId>()
    private val started = mutableListOf<TrainingPlanVersionId?>()
    private var opened = 0
    private var createPlan = 0
    private var logRecovery = 0

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun show(state: HomeUiState) {
        composeRule.setContent {
            RepFlowTheme {
                HomeScreen(
                    uiState = state,
                    onSettingsClick = {},
                    onResumeClick = { opened++ },
                    onFinishClick = { opened++ },
                    onAbandonConfirmed = { abandoned += it },
                    onStartWorkout = { started += it },
                    onCreatePlanClick = { createPlan++ },
                    onLogRecoveryClick = { logRecovery++ },
                    onRetryHistory = {},
                    onErrorShown = {},
                )
            }
        }
    }

    @Test
    fun theResumeCardsTrashAbandonsOnlyAfterTheConfirmation() {
        val session = WorkoutSessionId("session-1")
        show(
            HomeUiState(
                date = today,
                activeWorkout = HomeActiveWorkout(session, planName = "Push day", startedAt = Instant.now(), setsLogged = 3),
            ),
        )
        composeRule.onNodeWithText("Push day — still running").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(string(R.string.home_abandon_content_description)).performClick()
        composeRule.onNodeWithText(string(R.string.workout_abandon_confirm_title)).assertIsDisplayed()
        assertEquals(emptyList<WorkoutSessionId>(), abandoned)

        composeRule.onNodeWithText(string(R.string.workout_abandon_keep_action)).performClick()
        assertEquals(emptyList<WorkoutSessionId>(), abandoned)

        composeRule.onNodeWithContentDescription(string(R.string.home_abandon_content_description)).performClick()
        composeRule.onNodeWithText(string(R.string.workout_abandon_confirm_action)).performClick()
        assertEquals(listOf(session), abandoned)
    }

    @Test
    fun resumeAndFinishItBothOpenTheWorkoutAndNoStartCardIsDrawn() {
        show(
            HomeUiState(
                date = today,
                activeWorkout = HomeActiveWorkout(WorkoutSessionId("s"), planName = null, startedAt = Instant.now(), setsLogged = 0),
                start = HomeStartCard.Plan(push),
                startOptions = listOf(push),
            ),
        )
        composeRule.onNodeWithText(string(R.string.home_start_workout)).assertDoesNotExist()

        composeRule.onNodeWithText(string(R.string.home_resume)).performClick()
        composeRule.onNodeWithText(string(R.string.home_finish_it)).performClick()

        assertEquals(2, opened)
    }

    @Test
    fun startWorkoutStartsTheCardsPlanAndTheSheetOffersEveryPlanAndAnEmptyWorkout() {
        show(HomeUiState(date = today, start = HomeStartCard.Plan(push), startOptions = listOf(push, legs)))

        composeRule.onNodeWithText(string(R.string.home_start_workout)).performClick()
        composeRule.onNodeWithText(string(R.string.home_train_something_else)).performClick()
        composeRule.onNodeWithText("Leg day").performClick()
        composeRule.onNodeWithText(string(R.string.home_train_something_else)).performClick()
        composeRule.onNodeWithText(string(R.string.home_empty_workout)).performClick()

        assertEquals(listOf(push.versionId, legs.versionId, null), started)
    }

    @Test
    fun withNoPlanTheFirstRunCardCreatesAPlanOrStartsAnEmptyWorkout() {
        show(HomeUiState(date = today, start = HomeStartCard.NoPlan))

        composeRule.onNodeWithText(string(R.string.home_first_run_message)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_create_plan)).performClick()
        composeRule.onNodeWithText(string(R.string.home_empty_workout)).performClick()

        assertEquals(1, createPlan)
        assertEquals(listOf<TrainingPlanVersionId?>(null), started)
    }

    @Test
    fun theScoreOpensTheReadinessSheetAndTheEmptyStateLogsRecovery() {
        show(HomeUiState(date = today, readiness = HomeReadiness.Logged(seedReadiness())))
        val description =
            composeRule.activity.getString(R.string.home_readiness_content_description, 75, string(R.string.readiness_band_ready))

        composeRule.onNodeWithText("Sleep 4").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(description).performClick()
        composeRule.onNodeWithText(string(R.string.readiness_sheet_title)).assertIsDisplayed()
    }

    @Test
    fun withNothingLoggedTodayTheRecoveryCardOffersTheLog() {
        show(HomeUiState(date = today, readiness = HomeReadiness.NotLogged))

        composeRule.onNodeWithText(string(R.string.home_recovery_empty_message)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_recovery_empty_action)).performClick()

        assertEquals(1, logRecovery)
    }

    /** An empty Recovery card hides the header `Log ›`; the one entry point is `Log recovery` (P2-F-6). */
    @Test
    fun anEmptyRecoveryCardHidesTheHeaderLogLink() {
        show(HomeUiState(date = today, readiness = HomeReadiness.NotLogged))

        composeRule.onNodeWithText(string(R.string.home_recovery_empty_action)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.home_recovery_log_content_description)).assertDoesNotExist()
    }

    /** With an entry the header `Log ›` stays. */
    @Test
    fun aLoggedRecoveryCardKeepsTheHeaderLogLink() {
        show(HomeUiState(date = today, readiness = HomeReadiness.Logged(seedReadiness())))

        composeRule.onNodeWithContentDescription(string(R.string.home_recovery_log_content_description)).assertIsDisplayed()
    }

    /** At 1.3x and 2.0x font the resume card's `Resume` and `Finish it` stay whole words on one line (design 9f, P2-F-3). */
    @Test
    fun theResumeCardsLabelsStayWholeAtLargeFontSizes() {
        val scale = mutableStateOf(LARGE_FONT_SCALE_MEDIUM)
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale.value)) {
                RepFlowTheme {
                    Box(Modifier.width(384.dp)) {
                        ResumeCard(
                            workout =
                                HomeActiveWorkout(
                                    WorkoutSessionId("s"),
                                    planName = "Push day",
                                    startedAt = Instant.now(),
                                    setsLogged = 3,
                                ),
                            onResumeClick = {},
                            onFinishClick = {},
                            onAbandonClick = {},
                        )
                    }
                }
            }
        }
        listOf(LARGE_FONT_SCALE_MEDIUM, LARGE_FONT_SCALE_MAX).forEach { fontScale ->
            composeRule.runOnIdle { scale.value = fontScale }
            composeRule.waitForIdle()
            listOf(R.string.home_resume, R.string.home_finish_it).forEach { label ->
                val results = mutableListOf<TextLayoutResult>()
                val node = composeRule.onNodeWithText(string(label))
                node.assertIsDisplayed()
                node
                    .fetchSemanticsNode()
                    .config[SemanticsActions.GetTextLayoutResult]
                    .action
                    ?.invoke(results)
                assertEquals("label $label wraps at font $fontScale", 1, results.single().lineCount)
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue("label $label is clipped at font $fontScale: $bounds", bounds.left >= 0.dp && bounds.right <= 384.dp)
            }
        }
    }

    /** The prototype's seed check-in: 75, Ready. */
    private fun seedReadiness(): ReadinessScore {
        val at = Instant.parse("2026-08-11T07:00:00Z")
        val entry =
            RecoveryEntry.create(
                id = RecoveryEntryId("recovery-1"),
                date = today,
                sleepQuality = 4,
                energy = 3,
                legDoms = 2,
                heelStiffness = 1,
                painWhileWalking = 0,
                heavyLegs = 2,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = at,
                updatedAt = at,
            )
        return ReadinessScore.of((entry as DomainResult.Success).value)
    }
}

private const val LARGE_FONT_SCALE_MEDIUM = 1.3f
private const val LARGE_FONT_SCALE_MAX = 2.0f
