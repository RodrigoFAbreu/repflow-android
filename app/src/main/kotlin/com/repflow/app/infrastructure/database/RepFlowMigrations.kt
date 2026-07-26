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

/**
 * The real, additive 2-to-3 migration introducing the active-workout tables
 * (see `docs/milestones/active/milestone-3-reference.md`'s data model
 * section). Nothing about the existing tables changes. No
 * `fallbackToDestructiveMigration` call exists anywhere (see
 * [RepFlowDatabase]'s KDoc).
 */
@Suppress("MagicNumber")
val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createWorkoutSessionsTable(db)
            createWorkoutExercisesTable(db)
            createWorkoutSetsTable(db)
        }

        private fun createWorkoutSessionsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_sessions` (
                    `id` TEXT NOT NULL,
                    `training_plan_version_id` TEXT,
                    `status` TEXT NOT NULL,
                    `started_at` INTEGER NOT NULL,
                    `ended_at` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`training_plan_version_id`) REFERENCES `training_plan_versions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_workout_sessions_training_plan_version_id`
                ON `workout_sessions` (`training_plan_version_id`)
                """.trimIndent(),
            )
        }

        private fun createWorkoutExercisesTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_exercises` (
                    `id` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `exercise_id` TEXT NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    `exercise_name_snapshot` TEXT NOT NULL,
                    `tracking_type` TEXT NOT NULL,
                    `planned_exercise_id` TEXT,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`session_id`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                    FOREIGN KEY(`planned_exercise_id`) REFERENCES `planned_exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_exercises_session_order`
                ON `workout_exercises` (`session_id`, `sort_order`)
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_exercises_session_id` ON `workout_exercises` (`session_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_exercises_exercise_id` ON `workout_exercises` (`exercise_id`)",
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_workout_exercises_planned_exercise_id`
                ON `workout_exercises` (`planned_exercise_id`)
                """.trimIndent(),
            )
        }

        private fun createWorkoutSetsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workout_sets` (
                    `id` TEXT NOT NULL,
                    `workout_exercise_id` TEXT NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    `load` REAL,
                    `reps` INTEGER,
                    `duration_seconds` INTEGER,
                    `rpe` REAL,
                    `is_warmup` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`workout_exercise_id`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_sets_exercise_order`
                ON `workout_sets` (`workout_exercise_id`, `sort_order`)
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_sets_exercise_id` ON `workout_sets` (`workout_exercise_id`)",
            )
        }
    }

/**
 * The real, additive 4-to-5 migration introducing the recovery and futsal
 * tables (see `docs/milestones/completed/milestone-5-reference.md`'s data
 * model section). Nothing about the existing tables changes. No
 * `fallbackToDestructiveMigration` call exists anywhere (see
 * [RepFlowDatabase]'s KDoc).
 */
@Suppress("MagicNumber")
val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recovery_entries` (
                    `id` TEXT NOT NULL,
                    `entry_date` TEXT NOT NULL,
                    `sleep_quality` INTEGER NOT NULL,
                    `energy` INTEGER NOT NULL,
                    `leg_doms` INTEGER NOT NULL,
                    `heel_stiffness` INTEGER NOT NULL,
                    `pain_while_walking` INTEGER NOT NULL,
                    `heavy_legs` INTEGER NOT NULL,
                    `futsal_in_previous_24h` INTEGER NOT NULL,
                    `futsal_expected_next_24h` INTEGER NOT NULL,
                    `notes` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_recovery_entries_entry_date`
                ON `recovery_entries` (`entry_date`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `futsal_sessions` (
                    `id` TEXT NOT NULL,
                    `entry_date` TEXT NOT NULL,
                    `duration_minutes` INTEGER NOT NULL,
                    `session_rpe` REAL NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_futsal_sessions_entry_date`
                ON `futsal_sessions` (`entry_date`)
                """.trimIndent(),
            )
        }
    }

/**
 * The real, additive 3-to-4 migration adding the rest-timer's absolute end
 * timestamp and total duration to `workout_sessions` (see
 * `docs/milestones/active/milestone-4-reference.md`'s data model section).
 * Both new columns are nullable; existing rows simply get `NULL` (no active
 * rest timer), which is exactly the correct default. No
 * `fallbackToDestructiveMigration` call exists anywhere (see
 * [RepFlowDatabase]'s KDoc).
 */
@Suppress("MagicNumber")
val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `rest_timer_end_at_epoch_ms` INTEGER")
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `rest_timer_total_duration_seconds` INTEGER")
        }
    }
