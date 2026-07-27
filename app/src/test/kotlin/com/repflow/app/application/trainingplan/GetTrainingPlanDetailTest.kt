package com.repflow.app.application.trainingplan

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetTrainingPlanDetailTest {
    private val planRepository = InMemoryTrainingPlanRepository()
    private val getTrainingPlanDetail = GetTrainingPlanDetail(planRepository)

    @Test
    fun `returns not found for a missing plan`() =
        runTest {
            val result = getTrainingPlanDetail(TrainingPlanId("missing-plan"))

            assertEquals(
                TrainingPlanOperationError.NotFound,
                (result as DomainResult.Failure).error,
            )
        }
}
