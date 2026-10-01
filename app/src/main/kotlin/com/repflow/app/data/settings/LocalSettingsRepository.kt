package com.repflow.app.data.settings

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.SettingsPersistenceError
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.settings.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The [SettingsRepository] implementation over the single pinned `settings`
 * row (remediation-1 CP14). Every install has that row - `MIGRATION_7_8` seeds
 * it on upgrade, `SETTINGS_SEED_CALLBACK` on a fresh install, and neither a
 * restore nor `Erase all data` deletes it - but a read never depends on that:
 * an absent row reads as [AppSettings.DEFAULT], and [update] writes the whole
 * row back, recreating it.
 */
class LocalSettingsRepository
    @Inject
    constructor(
        private val database: RepFlowDatabase,
    ) : SettingsRepository {
        override fun observe(): Flow<AppSettings> =
            database
                .settingsDao()
                .observe()
                .map { entity -> entity?.toSettings() ?: AppSettings.DEFAULT }
                .distinctUntilChanged()

        override suspend fun get(): AppSettings = database.settingsDao().find()?.toSettings() ?: AppSettings.DEFAULT

        override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit, SettingsPersistenceError> =
            try {
                database.withTransaction {
                    database.settingsDao().upsert(transform(get()).toEntity())
                }
                DomainResult.Success(Unit)
            } catch (expected: SQLiteFullException) {
                DomainResult.Failure(SettingsPersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                DomainResult.Failure(SettingsPersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                DomainResult.Failure(SettingsPersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                DomainResult.Failure(SettingsPersistenceError.Unavailable)
            }
    }

private fun SettingsEntity.toSettings(): AppSettings =
    AppSettings(
        restTimerAutoStart = restTimerAutoStart,
        restTimerVibrate = restTimerVibrate,
        restTimerNotification = restTimerNotification,
        keepScreenAwake = keepScreenAwake,
        confirmBeforeFinishing = confirmBeforeFinishing,
    )

private fun AppSettings.toEntity(): SettingsEntity =
    SettingsEntity(
        restTimerAutoStart = restTimerAutoStart,
        restTimerVibrate = restTimerVibrate,
        restTimerNotification = restTimerNotification,
        keepScreenAwake = keepScreenAwake,
        confirmBeforeFinishing = confirmBeforeFinishing,
    )
