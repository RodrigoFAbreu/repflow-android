package com.repflow.app.application.trainingplan

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
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
    fun observeOverviews(): Flow<List<TrainingPlanOverview>>

    suspend fun findOverviewByPlanId(id: TrainingPlanId): TrainingPlanOverview?

    suspend fun findPlanIdByNameKey(nameKey: String): TrainingPlanId?

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
}
