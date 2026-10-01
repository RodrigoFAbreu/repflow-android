package com.repflow.app.presentation.progression

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.common.Clock
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.progression.ProgressionRecommendationRepository
import com.repflow.app.application.progression.RecordManualOverride
import com.repflow.app.application.recovery.ObserveReadiness
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.recovery.ReadinessScore
import com.repflow.app.presentation.navigation.RepFlowDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * Drives the recommendation screen (remediation-1 CP6) for the exercise named
 * by the route's [RepFlowDestinations.PROGRESSION_EXERCISE_ARG].
 *
 * It reads the exercise's **latest** recommendation - the same one the
 * workout picker's row summarises - and writes the user's choice through the
 * existing [RecordManualOverride], the call the picker's inline buttons made
 * before CP6 moved them here. No domain or application concept is added: the
 * policy's result and reasons are shown as recorded, and a choice is a
 * `ManualOverride` on that recommendation, nothing else.
 *
 * **Readiness is read for one outcome only** (plan CP6 item 4): a
 * `RecoveryAdjustment` explains itself with a recovery fact, so its state
 * carries today's [ReadinessScore] for the drivers line and the readiness
 * sheet. Every other outcome subscribes to nothing. "Today" is read from the
 * [Clock] when the recommendation loads - the screen is opened from a workout
 * and left again, so it does not follow midnight the way Home does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressionRecommendationViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: ProgressionRecommendationRepository,
        private val getExercise: GetExercise,
        private val recordManualOverride: RecordManualOverride,
        private val observeReadiness: ObserveReadiness,
        private val clock: Clock,
    ) : ViewModel() {
        private val exerciseId =
            ExerciseId(checkNotNull(savedStateHandle.get<String>(RepFlowDestinations.PROGRESSION_EXERCISE_ARG)))

        private val reload = MutableStateFlow(0)
        private val choosing = MutableStateFlow(false)
        private val saving = MutableStateFlow(false)
        private val error = MutableStateFlow<RecommendationErrorReason?>(null)

        private val content: Flow<RecommendationContent> =
            reload.flatMapLatest {
                flow { emit(load()) }
                    .flatMapLatest { loaded -> withReadiness(loaded) }
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        emit(RecommendationContent.Failed)
                    }
            }

        val uiState: StateFlow<ProgressionRecommendationUiState> =
            combine(content, choosing, saving, error) { loaded, isChoosing, isSaving, err ->
                ProgressionRecommendationUiState(
                    content = loaded,
                    choosing = isChoosing && loaded is RecommendationContent.Loaded,
                    saving = isSaving,
                    error = err,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ProgressionRecommendationUiState(),
            )

        /** `Pick another load` / `Change my mind`: opens the `Your call` options. */
        fun onChooseAnother() {
            choosing.value = true
        }

        /** The options' `Back`, or the system back while they are open. */
        fun onCloseChoices() {
            choosing.value = false
        }

        /** `Keep the same load` (`6a`'s `Keep 80 kg`): an override to maintain. */
        fun onKeepSameLoad() {
            onPick(ProgressionResultUi.MAINTAIN_LOAD)
        }

        /**
         * Records [option] as the user's final say, through [RecordManualOverride].
         *
         * Picking what is already in force writes nothing and only closes the
         * options: the suggestion when nothing was chosen yet (going with the
         * suggestion is not an override), or the existing choice again. Picking
         * the suggestion *after* an override does write - an override can be
         * replaced but not removed, so that is how a change of mind back to the
         * suggestion is put on record.
         */
        fun onPick(option: ProgressionResultUi) {
            val loaded = uiState.value.content as? RecommendationContent.Loaded ?: return
            if (saving.value) return
            if (option == loaded.inForce) {
                choosing.value = false
                return
            }
            viewModelScope.launch {
                saving.value = true
                when (recordManualOverride(exerciseId, option.toDomain())) {
                    is DomainResult.Success -> {
                        choosing.value = false
                        reload.value += 1
                    }

                    is DomainResult.Failure -> {
                        error.value = RecommendationErrorReason.SAVE_FAILED
                    }
                }
                saving.value = false
            }
        }

        fun onRetry() {
            reload.value += 1
        }

        fun onErrorShown() {
            error.value = null
        }

        private suspend fun load(): RecommendationContent {
            val recommendation = repository.findLatestForExercise(exerciseId) ?: return RecommendationContent.NotFound
            val name = (getExercise(exerciseId) as? DomainResult.Success)?.value?.name?.value
            return recommendation.toLoaded(name)
        }

        private fun withReadiness(content: RecommendationContent): Flow<RecommendationContent> {
            if (content !is RecommendationContent.Loaded || content.suggested != ProgressionResultUi.RECOVERY_ADJUSTMENT) {
                return flowOf(content)
            }
            val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
            return observeReadiness(today)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(null)
                }.map { score -> content.copy(readiness = score) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun ProgressionRecommendation.toLoaded(exerciseName: String?): RecommendationContent.Loaded =
    RecommendationContent.Loaded(
        exerciseName = exerciseName,
        suggested = result.toUi(),
        reasons = reasons,
        policyVersion = policyVersion,
        choice = manualOverride?.let { RecommendationChoice(result = it.result.toUi(), at = it.overriddenAt) },
    )
