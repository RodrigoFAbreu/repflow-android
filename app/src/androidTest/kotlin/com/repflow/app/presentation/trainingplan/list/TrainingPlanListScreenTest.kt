package com.repflow.app.presentation.trainingplan.list

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.trainingplan.TrainingPlanId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stateless Compose coverage for [TrainingPlanListScreen], mirroring
 * [com.repflow.app.presentation.exercise.list.ExerciseListScreenTest]'s
 * shape.
 */
@RunWith(AndroidJUnit4::class)
class TrainingPlanListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        uiState: TrainingPlanListUiState,
        onRetry: () -> Unit = {},
        onPlanClick: (TrainingPlanId) -> Unit = {},
        onCreateClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            TrainingPlanListScreen(
                uiState = uiState,
                onRetry = onRetry,
                onPlanClick = onPlanClick,
                onCreateClick = onCreateClick,
            )
        }
    }

    @Test
    fun rendersContentRowsAndInvokesOnPlanClick() {
        var clickedId: TrainingPlanId? = null
        val item = TrainingPlanListItem(id = TrainingPlanId("1"), name = "Push Pull Legs", plannedExerciseCount = 3)
        setContent(
            uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Content(listOf(item))),
            onPlanClick = { clickedId = it },
        )

        composeRule.onNodeWithText("Push Pull Legs").assertIsDisplayed()
        composeRule.onNodeWithText("Push Pull Legs").performClick()

        assertEquals(item.id, clickedId)
    }

    @Test
    fun rendersTheEmptyState() {
        setContent(uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Empty))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_empty))
            .assertIsDisplayed()
    }

    @Test
    fun rendersTheObservationFailedPanelAndInvokesRetry() {
        var retried = false
        setContent(
            uiState =
                TrainingPlanListUiState(
                    content = TrainingPlanListContent.ObservationFailed(TrainingPlanListFailureReason.UNKNOWN),
                ),
            onRetry = { retried = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_observation_failed))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_retry))
            .performClick()

        assertEquals(true, retried)
    }

    @Test
    fun createFabClickInvokesOnCreateClick() {
        var created = false
        setContent(
            uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Empty),
            onCreateClick = { created = true },
        )

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.training_plan_list_add_content_description),
            ).performClick()

        assertEquals(true, created)
    }
}
