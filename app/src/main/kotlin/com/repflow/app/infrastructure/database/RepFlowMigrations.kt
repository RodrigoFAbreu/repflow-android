package com.repflow.app.infrastructure.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The real, additive 1-to-2 migration introducing the training-plan tables
 * (see `docs/milestones/active/milestone-2-reference.md`'s persistence
 * section for the schema decisions). Nothing about the existing `exercises`
 * table changes.
 *
 * This is the first real [Migration] in the project - version 1 never had
 * one, and there is no `fallbackToDestructiveMigration` call anywhere (see
 * [RepFlowDatabase]'s KDoc).
 */
val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createTrainingPlansTable(db)
            createTrainingPlanVersionsTable(db)
            createPlannedExercisesTable(db)
        }

        private fun createTrainingPlansTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_plans` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `name_key` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_training_plans_name_key` ON `training_plans` (`name_key`)",
            )
        }

        private fun createTrainingPlanVersionsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_plan_versions` (
                    `id` TEXT NOT NULL,
                    `plan_id` TEXT NOT NULL,
                    `version_number` INTEGER NOT NULL,
                    `note` TEXT,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`plan_id`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_training_plan_versions_plan_version`
                ON `training_plan_versions` (`plan_id`, `version_number`)
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_training_plan_versions_plan_id` ON `training_plan_versions` (`plan_id`)",
            )
        }

        private fun createPlannedExercisesTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `planned_exercises` (
                    `id` TEXT NOT NULL,
                    `version_id` TEXT NOT NULL,
                    `exercise_id` TEXT NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    `target_sets` INTEGER NOT NULL,
                    `target_kind` TEXT NOT NULL,
                    `rep_min` INTEGER,
                    `rep_max` INTEGER,
                    `duration_min_seconds` INTEGER,
                    `duration_max_seconds` INTEGER,
                    `rest_seconds` INTEGER,
                    `is_optional` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`version_id`) REFERENCES `training_plan_versions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_planned_exercises_version_order`
                ON `planned_exercises` (`version_id`, `sort_order`)
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_planned_exercises_version_id` ON `planned_exercises` (`version_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_planned_exercises_exercise_id` ON `planned_exercises` (`exercise_id`)",
            )
        }
    }
