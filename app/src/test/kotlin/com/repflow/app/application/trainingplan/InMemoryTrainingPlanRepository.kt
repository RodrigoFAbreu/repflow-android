package com.repflow.app.application.trainingplan

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [TrainingPlanRepository] fake for use-case tests, mirroring
 * [com.repflow.app.application.exercise.InMemoryExerciseRepository]'s shape.
 *
 * [nextCreateFailure] / [nextAddVersionFailure] let a test force the next
 * write to fail as the real repository would after losing a race to the
 * database's `UNIQUE` constraint, even though the use case's own precheck
 * passed.
 */
class InMemoryTrainingPlanRepository : TrainingPlanRepository {
    private val plans = MutableStateFlow<Map<TrainingPlanId, TrainingPlan>>(emptyMap())
    private val versionsByPlan = MutableStateFlow<Map<TrainingPlanId, List<TrainingPlanVersion>>>(emptyMap())

    var nextCreateFailure: TrainingPlanPersistenceError? = null
    var nextAddVersionFailure: TrainingPlanPersistenceError? = null

    override fun observeOverviews(): Flow<List<TrainingPlanOverview>> =
        plans.map { plansById ->
            plansById.values
                .mapNotNull { plan -> latestVersionOf(plan.id)?.let { version -> TrainingPlanOverview(plan, version) } }
                .sortedWith(compareBy({ it.plan.name.key }, { it.plan.id.value }))
        }

    override suspend fun findOverviewByPlanId(id: TrainingPlanId): TrainingPlanOverview? {
        val plan = plans.value[id] ?: return null
        val version = latestVersionOf(id) ?: return null
        return TrainingPlanOverview(plan, version)
    }

    override suspend fun findPlanIdByNameKey(nameKey: String): TrainingPlanId? =
        plans.value.values
            .firstOrNull { it.name.key == nameKey }
            ?.id

    override suspend fun findPlannedExercise(id: PlannedExerciseId): PlannedExercise? =
        versionsByPlan.value.values
            .flatten()
            .flatMap { it.plannedExercises }
            .firstOrNull { it.id == id }

    @Suppress("ReturnCount")
    override suspend fun createPlanWithFirstVersion(
        plan: TrainingPlan,
        version: TrainingPlanVersion,
    ): DomainResult<Unit, TrainingPlanPersistenceError> {
        nextCreateFailure?.let {
            nextCreateFailure = null
            return DomainResult.Failure(it)
        }
        if (plans.value.containsKey(plan.id) || hasConflictingNameKey(plan)) {
            return DomainResult.Failure(TrainingPlanPersistenceError.DuplicateName)
        }
        plans.value = plans.value + (plan.id to plan)
        versionsByPlan.value = versionsByPlan.value + (plan.id to listOf(version))
        return DomainResult.Success(Unit)
    }

    @Suppress("ReturnCount")
    override suspend fun addVersion(
        plan: TrainingPlan,
        version: TrainingPlanVersion,
    ): DomainResult<Unit, TrainingPlanPersistenceError> {
        nextAddVersionFailure?.let {
            nextAddVersionFailure = null
            return DomainResult.Failure(it)
        }
        if (!plans.value.containsKey(plan.id)) {
            return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
        }
        if (hasConflictingNameKey(plan)) {
            return DomainResult.Failure(TrainingPlanPersistenceError.DuplicateName)
        }
        plans.value = plans.value + (plan.id to plan)
        versionsByPlan.value = versionsByPlan.value + (plan.id to (versionsByPlan.value[plan.id].orEmpty() + version))
        return DomainResult.Success(Unit)
    }

    /** Direct read of every persisted version, oldest first - for tests asserting history preservation. */
    fun versionsOf(planId: TrainingPlanId): List<TrainingPlanVersion> = versionsByPlan.value[planId].orEmpty()

    private fun latestVersionOf(planId: TrainingPlanId): TrainingPlanVersion? =
        versionsByPlan.value[planId]?.maxByOrNull { it.versionNumber }

    private fun hasConflictingNameKey(plan: TrainingPlan): Boolean =
        plans.value.values.any { it.id != plan.id && it.name.key == plan.name.key }
}
