package com.repflow.app.application.settings

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.flow.Flow

/**
 * The application's capability contract for the device's preferences
 * (remediation-1 CP14). Implemented over a Room table - the same offline
 * source of truth as everything else (ADR-0002), not a second persistence
 * mechanism. No Room type is visible here (see `LayerBoundaryTest`).
 */
interface SettingsRepository {
    /** The current values, re-emitted on every change. */
    fun observe(): Flow<AppSettings>

    /** The current values, read once - for a behaviour that must use the value at the moment it acts. */
    suspend fun get(): AppSettings

    /** Applies [transform] to the current values and stores the result, atomically. */
    suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit, SettingsPersistenceError>
}

/** Why a settings write did not land. */
sealed interface SettingsPersistenceError {
    /** Local storage could not complete the write; nothing changed. */
    data object Unavailable : SettingsPersistenceError
}
