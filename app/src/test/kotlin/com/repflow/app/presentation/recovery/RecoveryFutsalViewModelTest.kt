package com.repflow.app.presentation.recovery

import com.repflow.app.application.recovery.FixedClock
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.recovery.RecordFutsalSession
import com.repflow.app.application.recovery.RecordRecoveryEntry
import com.repflow.app.application.recovery.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
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
import org.junit.Assert.assertFalse
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

    private fun today(): LocalDate = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()

    @Test
    fun `onSaveEntry persists the current scale values`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onScaleFieldChanged(RecoveryScaleField.SLEEP_QUALITY, 4)
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            val stored = recoveryRepository.findForDate(today())
            assertEquals(4, stored?.sleepQuality)
            assertTrue(viewModel.uiState.value.isEntrySaved)
        }

    @Test
    fun `onSaveEntry with played in last 24h persists duration and rpe and computes load`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onFutsalPreviousToggled(true)
            viewModel.onDurationChanged("60")
            viewModel.onSessionRpeChanged("7")
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            val stored = futsalRepository.findForDate(today())
            assertEquals(60, stored?.durationMinutes)
            assertEquals(420.0, stored?.load ?: 0.0, 0.0)
            assertEquals(true, recoveryRepository.findForDate(today())?.futsalInPrevious24h)
            assertTrue(viewModel.uiState.value.isEntrySaved)
        }

    @Test
    fun `onSaveEntry with non numeric futsal input surfaces an error without writing`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onFutsalPreviousToggled(true)
            viewModel.onDurationChanged("not-a-number")
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.errorMessage != null)
            assertFalse(viewModel.uiState.value.isEntrySaved)
            assertEquals(null, futsalRepository.findForDate(today()))
            assertEquals(null, recoveryRepository.findForDate(today()))
        }

    @Test
    fun `onSaveEntry ignores a second call while the first save is still in flight`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onSaveEntry()
            assertTrue(viewModel.uiState.value.isSaving)
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.isEntrySaved)
        }

    @Test
    fun `onSaveEntry with a futsal session ignores a second call while the first save is still in flight`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onFutsalPreviousToggled(true)
            viewModel.onDurationChanged("60")
            viewModel.onSessionRpeChanged("7")

            viewModel.onSaveEntry()
            assertTrue(viewModel.uiState.value.isSaving)
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.isEntrySaved)
            assertEquals(60, futsalRepository.findForDate(today())?.durationMinutes)
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
            setupViewModel.onSaveEntry()
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
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onDateChanged(LocalDate.of(2020, 1, 1))
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(RecoveryFutsalUiState.DEFAULT_SCALE_VALUE, viewModel.uiState.value.energy)
        }

    @Test
    fun `onSaveEntry persists a scale value up to the widened maximum of 5`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onScaleFieldChanged(RecoveryScaleField.SLEEP_QUALITY, 5)
            assertEquals(5, viewModel.uiState.value.sleepQuality)
            viewModel.onSaveEntry()
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
            firstViewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            val secondViewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(4, secondViewModel.uiState.value.energy)
            assertEquals(4, recoveryRepository.findForDate(today)?.energy)
        }

    @Test
    fun `onSaveEntry with played in last 24h and both futsal fields empty saves only the check-in`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onFutsalPreviousToggled(true)
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(true, recoveryRepository.findForDate(today())?.futsalInPrevious24h)
            assertEquals(null, futsalRepository.findForDate(today()))
            assertEquals(null, viewModel.uiState.value.errorMessage)
            assertTrue(viewModel.uiState.value.isEntrySaved)
        }

    @Test
    fun `onSaveEntry with played in last 24h off saves no futsal session whatever the fields hold`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onDurationChanged("60")
            viewModel.onSessionRpeChanged("7")
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, recoveryRepository.findForDate(today())?.futsalInPrevious24h)
            assertEquals(null, futsalRepository.findForDate(today()))
        }

    @Test
    fun `an edit after saving makes the entry unsaved again`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.onSaveEntry()
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isEntrySaved)

            viewModel.onNotesChanged("Slept badly")

            assertFalse(viewModel.uiState.value.isEntrySaved)
        }

    @Test
    fun `the date row knows today from the injected clock`() =
        runTest {
            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(today(), viewModel.uiState.value.today)
            assertEquals(today(), viewModel.uiState.value.date)
        }

    /**
     * Plan CP13 item 5: each scale row writes the value the readiness score
     * reads under the same factor, so a row's end labels (which follow that
     * factor's polarity) describe the number the score actually weighs.
     */
    @Test
    fun `every scale row shows the value its readiness factor reads`() =
        runTest {
            val at = Instant.parse("2026-01-01T08:00:00Z")
            val entry =
                (
                    RecoveryEntry.create(
                        id = RecoveryEntryId("seeded"),
                        date = today(),
                        sleepQuality = 0,
                        energy = 1,
                        legDoms = 2,
                        heelStiffness = 3,
                        painWhileWalking = 4,
                        heavyLegs = 5,
                        futsalInPrevious24h = false,
                        futsalExpectedNext24h = false,
                        notes = null,
                        createdAt = at,
                        updatedAt = at,
                    ) as DomainResult.Success
                ).value
            recoveryRepository.seed(entry)

            val viewModel = createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            RecoveryScaleField.entries.forEach { field ->
                assertEquals(field.name, field.readinessFactor.valueIn(entry), viewModel.uiState.value.valueOf(field))
            }
        }
}
