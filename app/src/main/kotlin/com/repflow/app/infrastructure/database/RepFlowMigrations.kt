package com.repflow.app.infrastructure.database

import androidx.room.RoomDatabase
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
 * The real, additive 5-to-6 migration adding the `progression_recommendations`
 * table (see `docs/milestones/active/milestone-6-reference.md`'s data
 * model section). Nothing about the existing tables changes. No
 * `fallbackToDestructiveMigration` call exists anywhere (see
 * [RepFlowDatabase]'s KDoc).
 */
@Suppress("MagicNumber")
val MIGRATION_5_6: Migration =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `progression_recommendations` (
                    `id` TEXT NOT NULL,
                    `exercise_id` TEXT NOT NULL,
                    `result` TEXT NOT NULL,
                    `reasons` TEXT NOT NULL,
                    `policy_version` INTEGER NOT NULL,
                    `computed_at` INTEGER NOT NULL,
                    `override_result` TEXT,
                    `override_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_progression_recommendations_exercise_id_computed_at`
                ON `progression_recommendations` (`exercise_id`, `computed_at`)
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

/**
 * The real, additive 6-to-7 migration adding five nullable columns across
 * four existing tables (see `docs/milestones/active/milestone-8-reference.md`):
 * pain and technique-quality feedback on `workout_sets`, a planned warm-up
 * set count on `planned_exercises`, a completed-workout invalidation
 * timestamp on `workout_sessions`, and a training-plan archive timestamp on
 * `training_plans`. All five are nullable; existing rows simply get `NULL`.
 * No `fallbackToDestructiveMigration` call exists anywhere (see
 * [RepFlowDatabase]'s KDoc).
 */
@Suppress("MagicNumber")
val MIGRATION_6_7: Migration =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workout_sets` ADD COLUMN `pain` INTEGER")
            db.execSQL("ALTER TABLE `workout_sets` ADD COLUMN `technique_quality` INTEGER")
            db.execSQL("ALTER TABLE `planned_exercises` ADD COLUMN `target_warmup_sets` INTEGER")
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `invalidated_at` INTEGER")
            db.execSQL("ALTER TABLE `training_plans` ADD COLUMN `archived_at` INTEGER")
        }
    }

/**
 * The default `settings` row (remediation-1 CP14): the values that keep
 * today's behaviour - rest auto-start on, vibrate on, notification on, keep
 * screen awake **off** (nothing kept the screen on before this table existed;
 * the design's `setAwake: true` is register `D21`) and confirm before
 * finishing on (CP9's unconditional confirmation). `INSERT OR IGNORE`, so a
 * row that already exists is never overwritten.
 *
 * A literal rather than a value built from the application's defaults: a
 * migration's SQL is frozen once shipped. `SettingsPersistenceTest` pins it
 * to `AppSettings.DEFAULT`.
 */
internal const val INSERT_DEFAULT_SETTINGS_ROW_SQL =
    "INSERT OR IGNORE INTO `settings` (`id`, `rest_timer_auto_start`, `rest_timer_vibrate`, " +
        "`rest_timer_notification`, `keep_screen_awake`, `confirm_before_finishing`) VALUES (1, 1, 1, 1, 0, 1)"

/**
 * The real, additive 7-to-8 migration introducing the single-row `settings`
 * table (remediation-1 CP14): one typed column per preference, the row pinned
 * at `id = 1`, seeded with [INSERT_DEFAULT_SETTINGS_ROW_SQL]. Nothing about
 * the existing tables changes. No `fallbackToDestructiveMigration` call
 * exists anywhere (see [RepFlowDatabase]'s KDoc).
 */
@Suppress("MagicNumber")
val MIGRATION_7_8: Migration =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `settings` (
                    `id` INTEGER NOT NULL,
                    `rest_timer_auto_start` INTEGER NOT NULL,
                    `rest_timer_vibrate` INTEGER NOT NULL,
                    `rest_timer_notification` INTEGER NOT NULL,
                    `keep_screen_awake` INTEGER NOT NULL,
                    `confirm_before_finishing` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(INSERT_DEFAULT_SETTINGS_ROW_SQL)
        }
    }

/**
 * The real, additive 8-to-9 migration (remediation-1-remediation-1 CP5): four
 * `ALTER TABLE settings ADD COLUMN` statements, each with a default (or
 * nullable) so the pinned row stays valid and no row is rewritten. Enum values
 * are stable strings. [INSERT_DEFAULT_SETTINGS_ROW_SQL] is deliberately
 * unchanged: `MIGRATION_7_8` runs it against the v8-shaped table.
 */
@Suppress("MagicNumber")
val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `settings` ADD COLUMN `theme` TEXT NOT NULL DEFAULT 'SYSTEM'")
            db.execSQL("ALTER TABLE `settings` ADD COLUMN `default_rest_seconds` INTEGER NOT NULL DEFAULT 90")
            db.execSQL("ALTER TABLE `settings` ADD COLUMN `extra_set_fields` TEXT NOT NULL DEFAULT 'COLLAPSED'")
            db.execSQL("ALTER TABLE `settings` ADD COLUMN `last_backup_at` INTEGER")
        }
    }

/**
 * Every migration, in order, shared by the production database builder and the
 * tests. A test pins it to [RepFlowDatabase.VERSION], so a future migration
 * cannot be forgotten.
 */
val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
    )

/**
 * Seeds the same default `settings` row on a fresh install, where Room creates
 * the schema at the current version and no migration runs - so every install,
 * upgraded or new, has the pinned row.
 */
val SETTINGS_SEED_CALLBACK: RoomDatabase.Callback =
    object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(INSERT_DEFAULT_SETTINGS_ROW_SQL)
        }
    }
