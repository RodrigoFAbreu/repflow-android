package com.repflow.app.infrastructure.database.exercise

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The Room-persisted row shape for an exercise (schema v1, see
 * plan.md section G).
 *
 * This type is a pure infrastructure detail - only [com.repflow.app.data.exercise.ExerciseEntityMapper]
 * and [com.repflow.app.data.exercise.LocalExerciseRepository] see it; no domain,
 * application or presentation code references it.
 *
 * `name` is the cleaned display form (NFKC normalized, trimmed, whitespace
 * collapsed, capitalization and accents preserved - D-26). `nameKey` is the
 * lower-cased identity/search form and carries the sole source of truth for
 * uniqueness via the `index_exercises_name_key` unique index (D-9: spans
 * active and archived rows). Ordering and search always operate on
 * `nameKey`, never on `name` and never via SQLite `COLLATE NOCASE` (D-23,
 * D-27).
 */
@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["name_key"], unique = true, name = "index_exercises_name_key"),
        Index(value = ["archived_at"]),
    ],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "tracking_type") val trackingType: String,
    val instructions: String?,
    @ColumnInfo(name = "default_load_increment_grams") val defaultLoadIncrementGrams: Long?,
    @ColumnInfo(name = "default_rest_seconds") val defaultRestSeconds: Long?,
    val origin: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
