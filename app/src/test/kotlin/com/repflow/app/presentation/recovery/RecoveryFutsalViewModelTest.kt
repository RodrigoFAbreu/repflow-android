package com.repflow.app.presentation.recovery

import com.repflow.app.application.recovery.FixedClock
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.recovery.RecordFutsalSession
import com.repflow.app.application.recovery.RecordRecoveryEntry
import com.repflow.app.application.recovery.SequentialIdentifierGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryFutsalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-01-01T12:00:00Z"))
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

    private fun createViewModel(): RecoveryFutsalViewModel =
        RecoveryFutsalViewModel(
            recoveryRepository = recoveryRepository,
            futsalRepository = futsalRepository,
            recordRecoveryEntry = RecordRecoveryEntry(recoveryRepository, clock, SequentialIdentifierGenerator("recovery")),
            recordFutsalSession = RecordFutsalSession(futsalRepository, clock, SequentialIdentifierGenerator("futsal")),
            clock = clock,
        )

    @Test
    fun `onSaveRecovery persists the current scale values`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onScaleFieldChanged(RecoveryScaleField.SLEEP_QUALITY, 4)
            viewModel.onSaveRecovery()
            dispatcher.scheduler.advanceUntilIdle()

            val stored = recoveryRepository.findForDate(clock.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate())
            assertEquals(4, stored?.sleepQuality)
            assertEquals("saved", viewModel.uiState.value.recoverySavedMessage)
        }

    @Test
    fun `onSaveFutsal persists duration and rpe and computes load`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onDurationChanged("60")
            viewModel.onSessionRpeChanged("7")
            viewModel.onSaveFutsal()
            dispatcher.scheduler.advanceUntilIdle()

            val stored = futsalRepository.findForDate(clock.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate())
            assertEquals(60, stored?.durationMinutes)
            assertEquals(420.0, stored?.load ?: 0.0, 0.0)
            assertEquals("saved", viewModel.uiState.value.futsalSavedMessage)
        }

    @Test
    fun `onSaveFutsal with non numeric input surfaces an error without writing`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onDurationChanged("not-a-number")
            viewModel.onSaveFutsal()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.errorMessage != null)
            assertEquals(null, futsalRepository.findForDate(clock.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate()))
        }

    @Test
    fun `onSaveRecovery ignores a second call while the first save is still in flight`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onSaveRecovery()
            assertTrue(viewModel.uiState.value.isSavingRecovery)
            viewModel.onSaveRecovery()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSavingRecovery)
            assertEquals("saved", viewModel.uiState.value.recoverySavedMessage)
        }

    @Test
    fun `onSaveFutsal ignores a second call while the first save is still in flight`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onDurationChanged("60")
            viewModel.onSessionRpeChanged("7")

            viewModel.onSaveFutsal()
            assertTrue(viewModel.uiState.value.isSavingFutsal)
            viewModel.onSaveFutsal()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSavingFutsal)
            assertEquals("saved", viewModel.uiState.value.futsalSavedMessage)
        }

    @Test
    fun `onDateChanged loads a previously saved entry for a past date`() =
        runTest {
            val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
            val yesterday = today.minusDays(1)
            val setupViewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            setupViewModel.onDateChanged(yesterday)
            dispatcher.scheduler.advanceUntilIdle()
            setupViewModel.onScaleFieldChanged(RecoveryScaleField.ENERGY, 5)
            setupViewModel.onSaveRecovery()
            dispatcher.scheduler.advanceUntilIdle()

            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onDateChanged(yesterday)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(yesterday, viewModel.uiState.value.date)
            assertEquals(5, viewModel.uiState.value.energy)
        }

    @Test
    fun `onDateChanged resets fields to defaults for a date with no saved entry`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onScaleFieldChanged(RecoveryScaleField.ENERGY, 5)
            viewModel.onSaveRecovery()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onDateChanged(LocalDate.of(2020, 1, 1))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(RecoveryFutsalUiState.DEFAULT_SCALE_VALUE, viewModel.uiState.value.energy)
        }

    @Test
    fun `onSaveRecovery persists a scale value up to the widened maximum of 5`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onScaleFieldChanged(RecoveryScaleField.SLEEP_QUALITY, 5)
            assertEquals(5, viewModel.uiState.value.sleepQuality)
            viewModel.onSaveRecovery()
            dispatcher.scheduler.advanceUntilIdle()

            val stored = recoveryRepository.findForDate(clock.now().atZone(ZoneId.systemDefault()).toLocalDate())
            assertEquals(5, stored?.sleepQuality)
        }

    @Test
    fun `initial load reloads previously saved values for today`() =
        runTest {
            val today = clock.now().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val firstViewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            firstViewModel.onScaleFieldChanged(RecoveryScaleField.ENERGY, 4)
            firstViewModel.onSaveRecovery()
            dispatcher.scheduler.advanceUntilIdle()

            val secondViewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(4, secondViewModel.uiState.value.energy)
            assertEquals(4, recoveryRepository.findForDate(today)?.energy)
        }
}
