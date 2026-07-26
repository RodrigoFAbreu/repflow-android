package com.repflow.app.infrastructure.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.repflow.app.infrastructure.database.exercise.ExerciseDao
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity

/**
 * RepFlow's single Room database. Version 1 - no migrations are registered
 * because none exist yet, and there is deliberately no
 * `fallbackToDestructiveMigration` anywhere in this codebase (see plan.md
 * section G and additional implementation correction 14: no fake v1
 * migration test). The first `MigrationTestHelper` test is written when
 * version 2 introduces a real 1-to-2 migration.
 */
@Database(entities = [ExerciseEntity::class], version = 1, exportSchema = true)
abstract class RepFlowDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
}
