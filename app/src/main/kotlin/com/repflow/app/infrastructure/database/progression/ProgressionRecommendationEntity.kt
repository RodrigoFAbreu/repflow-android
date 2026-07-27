package com.repflow.app.infrastructure.database.progression

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The Room-persisted row shape for a progression recommendation (schema
 * v6, see `docs/milestones/active/milestone-6-reference.md`). [reasons]
 * is stored as a `\n`-delimited string (never empty, mirroring the domain
 * invariant) since Room has no native list column type here.
 *
 * This type is a pure infrastructure detail - only
 * [com.repflow.app.data.progression.ProgressionRecommendationMapper] and
 * [com.repflow.app.data.progression.LocalProgressionRecommendationRepository]
 * see it.
 */
@Entity(
    tableName = "progression_recommendations",
    indices = [
        Index(value = ["exercise_id", "computed_at"], name = "index_progression_recommendations_exercise_id_computed_at"),
    ],
)
data class ProgressionRecommendationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val result: String,
    val reasons: String,
    @ColumnInfo(name = "policy_version") val policyVersion: Int,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
    @ColumnInfo(name = "override_result") val overrideResult: String?,
    @ColumnInfo(name = "override_at") val overrideAt: Long?,
)
