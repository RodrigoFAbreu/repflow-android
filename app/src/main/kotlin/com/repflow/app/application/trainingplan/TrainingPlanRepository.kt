package com.repflow.app.application.trainingplan

import com.repflow.app.domain.backup.TrainingPlanSnapshot
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import kotlinx.coroutines.flow.Flow

/**
 * The application's capability contract for persisting and querying
 * training plans and their versions. Implemented by the infrastructure/data
 * layer; no Room type is visible here (see `LayerBoundaryTest`).
 *
 * There is deliberately no method that mutates an existing
 * [TrainingPlanVersion] - [addVersion] only ever inserts a brand-new one,
 * preserving every previous version untouched (see the reference doc's
 * "historical version preservation" requirement).
 */
interface TrainingPlanRepository {
    fun observeOverviews(status: TrainingPlanStatusFilter): Flow<List<TrainingPlanOverview>>

    suspend fun findOverviewByPlanId(id: TrainingPlanId): TrainingPlanOverview?

    suspend fun findPlanIdByNameKey(nameKey: String): TrainingPlanId?

    /** Looks up a single planned exercise by id, used to fetch its target rep range for progression recommendations. */
    suspend fun findPlannedExercise(id: PlannedExerciseId): PlannedExercise?

    /** Every plan with every one of its versions (never just the latest), for backup export. */
    suspend fun findAllForBackup(): List<TrainingPlanSnapshot>

    /**
     * Every training-plan version's owning plan identity and current name,
     * reactively - re-emits on any plan/version insert, rename, or
     * archive/restore, including a full backup restore (Milestone 8,
     * implementation-review finding #5). Includes archived plans: History
     * must still label a session started from a plan that's since been
     * archived.
     */
    fun observeVersionLabels(): Flow<Map<TrainingPlanVersionId, TrainingPlanVersionLabel>>

    /**
     * How many non-archived plans hold each exercise in their **latest**
     * version, reactively - the exercise library's `in N plans` (remediation-1
     * CP10). An exercise no current plan holds is absent from the map rather
     * than mapped to zero. Earlier versions never count: they are history, and
     * a session started from one keeps it whatever the plan holds now.
     */
    fun observeExercisePlanUsage(): Flow<Map<ExerciseId, Int>>

    /** Atomically inserts [plan] and its first [version] (version 1). */
    suspend fun createPlanWithFirstVersion(
        plan: TrainingPlan,
        version: TrainingPlanVersion,
    ): DomainResult<Unit, TrainingPlanPersistenceError>

    /**
     * Atomically updates [plan]'s row (name/`updated_at`) and inserts
     * [version] as a brand-new row. Never touches an existing version.
     */
    suspend fun addVersion(
        plan: TrainingPlan,
        version: TrainingPlanVersion,
    ): DomainResult<Unit, TrainingPlanPersistenceError>

    /** Persists only [plan]'s own row (name/`updatedAt`/`archivedAt`) - never touches its versions. */
    suspend fun updatePlan(plan: TrainingPlan): DomainResult<Unit, TrainingPlanPersistenceError>
}
