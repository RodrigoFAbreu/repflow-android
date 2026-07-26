package com.repflow.app.domain.trainingplan

import org.junit.Test

class TrainingPlanIdTest {
    @Test(expected = IllegalArgumentException::class)
    fun `blank value is rejected`() {
        TrainingPlanId(" ")
    }

    @Test
    fun `non-blank value is accepted`() {
        TrainingPlanId("plan-1")
    }
}
