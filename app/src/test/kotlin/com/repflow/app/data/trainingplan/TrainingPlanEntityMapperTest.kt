package com.repflow.app.data.trainingplan

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanEntityMapperTest {
    private val planEntity =
        TrainingPlanEntity(
            id = "11111111-1111-1111-1111-111111111111",
            name = "Push Day",
            nameKey = "push day",
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )

    private val versionEntity =
        TrainingPlanVersionEntity(
            id = "22222222-2222-2222-2222-222222222222",
            planId = planEntity.id,
            versionNumber = 1,
            note = null,
            createdAt = 1_000L,
        )

    private val repsRow =
        PlannedExerciseEntity(
            id = "33333333-3333-3333-3333-333333333333",
            versionId = versionEntity.id,
            exerciseId = "44444444-4444-4444-4444-444444444444",
            sortOrder = 0,
            targetSets = 3,
            targetKind = "REPS",
            repMin = 8,
            repMax = 12,
            durationMinSeconds = null,
            durationMaxSeconds = null,
            restSeconds = 90,
            isOptional = false,
        )

    private val durationRow =
        PlannedExerciseEntity(
            id = "55555555-5555-5555-5555-555555555555",
            versionId = versionEntity.id,
            exerciseId = "66666666-6666-6666-6666-666666666666",
            sortOrder = 1,
            targetSets = 4,
            targetKind = "DURATION",
            repMin = null,
            repMax = null,
            durationMinSeconds = 30,
            durationMaxSeconds = 60,
            restSeconds = null,
            isOptional = true,
        )

    @Test
    fun `round-trips a plan through entity and back`() {
        val plan = requireSuccess(TrainingPlanEntityMapper.toDomain(planEntity))
        val roundTripped = TrainingPlanEntityMapper.toEntity(plan)

        assertEquals(planEntity, roundTripped)
    }

    @Test
    fun `round-trips a version with reps and duration rows through entity and back`() {
        val version = requireSuccess(TrainingPlanEntityMapper.toDomain(versionEntity, listOf(repsRow, durationRow)))

        val roundTrippedVersion = TrainingPlanEntityMapper.toEntity(version)
        val roundTrippedRows = TrainingPlanEntityMapper.toEntities(version)

        assertEquals(versionEntity, roundTrippedVersion)
        assertEquals(listOf(repsRow, durationRow), roundTrippedRows)
    }

    @Test
    fun `an unrecognised target kind fails the mapping with the row id`() {
        val result = TrainingPlanEntityMapper.toDomain(versionEntity, listOf(repsRow.copy(targetKind = "NOT_A_KIND")))

        val error = requireFailure(result)
        assertTrue(error is TrainingPlanMappingError.UnknownTargetKind)
        error as TrainingPlanMappingError.UnknownTargetKind
        assertEquals(repsRow.id, error.entityId)
        assertEquals("NOT_A_KIND", error.rawValue)
    }

    @Test
    fun `a reps row missing its bounds fails the mapping instead of being silently defaulted`() {
        val result = TrainingPlanEntityMapper.toDomain(versionEntity, listOf(repsRow.copy(repMin = null)))

        val error = requireFailure(result)
        assertTrue(error is TrainingPlanMappingError.InvalidFields)
    }

    @Test
    fun `an out-of-range target sets count fails the mapping`() {
        val result = TrainingPlanEntityMapper.toDomain(versionEntity, listOf(repsRow.copy(targetSets = 0)))

        val error = requireFailure(result)
        assertTrue(error is TrainingPlanMappingError.InvalidFields)
    }

    private fun <T> requireSuccess(result: DomainResult<T, TrainingPlanMappingError>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireFailure(result: DomainResult<T, TrainingPlanMappingError>): TrainingPlanMappingError =
        when (result) {
            is DomainResult.Success -> throw AssertionError("Expected failure but was success: ${result.value}")
            is DomainResult.Failure -> result.error
        }
}
