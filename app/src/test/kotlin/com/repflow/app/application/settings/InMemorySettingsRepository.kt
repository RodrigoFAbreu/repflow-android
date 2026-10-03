package com.repflow.app.application.settings

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory [SettingsRepository] fake: starts at [initial], and [nextUpdateFailure] fails the next write once. */
class InMemorySettingsRepository(
    initial: AppSettings = AppSettings.DEFAULT,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    var nextUpdateFailure: SettingsPersistenceError? = null

    override fun observe(): Flow<AppSettings> = state

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit, SettingsPersistenceError> {
        nextUpdateFailure?.let {
            nextUpdateFailure = null
            return DomainResult.Failure(it)
        }
        state.value = transform(state.value)
        return DomainResult.Success(Unit)
    }
}
