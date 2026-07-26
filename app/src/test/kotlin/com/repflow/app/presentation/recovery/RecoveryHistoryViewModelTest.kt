package com.repflow.app.presentation.recovery

import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.FutsalSessionId
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val recoveryRepository = InMemoryRecoveryRepository()
    private val futsalRepository = InMemoryFutsalRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads every recovery entry and futsal session`() =
        runTest {
            val createdAt = Instant.parse("2026-01-01T00:00:00Z")
            recoveryRepository.seed(
                requireSuccess(
                    RecoveryEntry.create(
                        id = RecoveryEntryId("r1"),
                        date = LocalDate.of(2026, 1, 1),
                        sleepQuality = 3,
                        energy = 2,
                        legDoms = 1,
                        heelStiffness = 0,
                        painWhileWalking = 0,
                        heavyLegs = 2,
                        futsalInPrevious24h = false,
                        futsalExpectedNext24h = false,
                        notes = null,
                        createdAt = createdAt,
                        updatedAt = createdAt,
                    ),
                ),
            )
            futsalRepository.seed(
                requireSuccess(
                    FutsalSession.create(
                        id = FutsalSessionId("f1"),
                        date = LocalDate.of(2026, 1, 2),
                        durationMinutes = 60,
                        sessionRpe = 7.0,
                        createdAt = createdAt,
                        updatedAt = createdAt,
                    ),
                ),
            )

            val viewModel = RecoveryHistoryViewModel(recoveryRepository, futsalRepository)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isLoading)
            assertEquals(1, viewModel.uiState.value.recoveryEntries.size)
            assertEquals(1, viewModel.uiState.value.futsalSessions.size)
        }

    @Test
    fun `starts loading and shows empty lists when nothing is recorded`() =
        runTest {
            val viewModel = RecoveryHistoryViewModel(recoveryRepository, futsalRepository)

            assertEquals(true, viewModel.uiState.value.isLoading)

            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isLoading)
            assertEquals(emptyList<RecoveryEntry>(), viewModel.uiState.value.recoveryEntries)
            assertEquals(emptyList<FutsalSession>(), viewModel.uiState.value.futsalSessions)
        }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T = (result as DomainResult.Success).value
}
