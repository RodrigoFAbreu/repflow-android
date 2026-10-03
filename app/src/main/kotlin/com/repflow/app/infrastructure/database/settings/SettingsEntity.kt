package com.repflow.app.infrastructure.database.settings

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The device's preferences (remediation-1 CP14): **one row, pinned at
 * [SINGLETON_ID], with one typed column per preference** - not a key/value
 * row per preference - so [com.repflow.app.infrastructure.database.MIGRATION_7_8]
 * is a plain `CREATE TABLE` plus the default row and nothing is parsed back
 * out of a `TEXT` blob. Every preference today is a switch, stored as SQLite's
 * `INTEGER` 0/1; a later enum-valued preference would be a `TEXT` column
 * holding a stable string, never an ordinal. Version 9 adds `theme`,
 * `extra_set_fields` (stable strings), `default_rest_seconds` and the nullable
 * `last_backup_at` (epoch milliseconds) via
 * [com.repflow.app.infrastructure.database.MIGRATION_8_9].
 *
 * These are device settings, not training data: the table is deliberately
 * outside the backup snapshot, and neither a restore nor `Erase all data`
 * touches it (`LocalTrainingDataRepository`).
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "rest_timer_auto_start")
    val restTimerAutoStart: Boolean,
    @ColumnInfo(name = "rest_timer_vibrate")
    val restTimerVibrate: Boolean,
    @ColumnInfo(name = "rest_timer_notification")
    val restTimerNotification: Boolean,
    @ColumnInfo(name = "keep_screen_awake")
    val keepScreenAwake: Boolean,
    @ColumnInfo(name = "confirm_before_finishing")
    val confirmBeforeFinishing: Boolean,
    @ColumnInfo(name = "theme", defaultValue = "SYSTEM")
    val theme: String = "SYSTEM",
    @ColumnInfo(name = "default_rest_seconds", defaultValue = "90")
    val defaultRestSeconds: Int = 90,
    @ColumnInfo(name = "extra_set_fields", defaultValue = "COLLAPSED")
    val extraSetFields: String = "COLLAPSED",
    @ColumnInfo(name = "last_backup_at")
    val lastBackupAt: Long? = null,
) {
    companion object {
        /** The one row's fixed primary key. */
        const val SINGLETON_ID = 1
    }
}
