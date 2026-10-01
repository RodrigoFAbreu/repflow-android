package com.repflow.app.presentation.workout

import com.repflow.app.application.history.BestSet
import com.repflow.app.application.history.ExerciseRecap
import com.repflow.app.application.history.PersonalBest
import com.repflow.app.application.history.WorkoutSummary
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.presentation.progression.ProgressionRecommendationUi
import java.math.BigDecimal
import java.time.Instant

/*
 * The done screen's plain values (remediation-1 CP9, `4a` `nDone`,
 * `RepFlow.dc.html:1364-1405`), kept out of the composables so a JVM test can
 * pin them. The prototype's figures are placeholders (`nRecapRows` says
 * `+2.5 kg` whenever the target was met, `nPrDetail` invents the previous
 * best); these are derived from the stored history by `ObserveWorkoutSummary`
 * (`D67`, `D68`).
 */

data class WorkoutDoneUiState(
    val content: WorkoutDoneContent = WorkoutDoneContent.Loading,
)

sealed interface WorkoutDoneContent {
    data object Loading : WorkoutDoneContent

    /** No completed session has the route's id (it was never completed, or a restore replaced it). */
    data object NotFound : WorkoutDoneContent

    data object Failed : WorkoutDoneContent

    /**
     * @property planName the plan the workout was started from, for the title;
     *   `null` for an ad-hoc workout (`Untitled workout`).
     * @property recommendations the progression recommendations this
     *   completion computed, one per exercise that has one (`D37`).
     */
    data class Loaded(
        val sessionId: WorkoutSessionId,
        val planName: String?,
        val startedAt: Instant,
        val endedAt: Instant,
        val workingSets: Int,
        val exercisesTrained: Int,
        val exerciseCount: Int,
        val personalBests: List<PersonalBest>,
        val recaps: List<RecapRowUi>,
        val recommendations: List<DoneRecommendationUi>,
    ) : WorkoutDoneContent
}

/** One `Versus last time` row: the exercise, what was logged, and how it compares. */
data class RecapRowUi(
    val id: WorkoutExerciseId,
    val name: String,
    val groups: List<RecapGroup>,
    val workingSets: Int,
    val warmups: Int,
    val delta: RecapDelta,
)

/**
 * A run of consecutive working sets read as one: `80 kg × 8, 8, 7`. A load
 * change starts a new run, so no set is shown at a load it was not lifted at.
 */
sealed interface RecapGroup {
    data class Loaded(
        val kg: BigDecimal,
        val reps: List<Int>,
    ) : RecapGroup

    data class Unloaded(
        val reps: List<Int>,
    ) : RecapGroup

    data class Timed(
        val seconds: List<Int>,
    ) : RecapGroup
}

/** The recap row's right-hand figure, against the last time the exercise was trained. */
sealed interface RecapDelta {
    /** No working set this time: the row is dimmed and reads `—`. */
    data object NoWorkingSets : RecapDelta

    /** No earlier session with a comparable best set. */
    data object FirstTime : RecapDelta

    data class Load(
        val kg: BigDecimal,
    ) : RecapDelta

    data class Reps(
        val reps: Int,
    ) : RecapDelta

    data class Seconds(
        val seconds: Int,
    ) : RecapDelta
}

/** One exercise's latest recommendation, as the done screen lists it, with its way into the recommendation screen. */
data class DoneRecommendationUi(
    val exerciseId: ExerciseId,
    val exerciseName: String,
    val recommendation: ProgressionRecommendationUi,
)

internal fun WorkoutSummary.toLoaded(
    planName: String?,
    recommendations: List<DoneRecommendationUi>,
): WorkoutDoneContent.Loaded {
    val recaps = recaps.map(::recapRowOf)
    return WorkoutDoneContent.Loaded(
        sessionId = session.id,
        planName = planName,
        startedAt = session.startedAt,
        endedAt = checkNotNull(session.endedAt),
        workingSets = recaps.sumOf { it.workingSets },
        exercisesTrained = recaps.count { it.workingSets > 0 },
        exerciseCount = recaps.size,
        personalBests = personalBests,
        recaps = recaps,
        recommendations = recommendations,
    )
}

internal fun recapRowOf(recap: ExerciseRecap): RecapRowUi {
    val working = recap.exercise.sets.filterNot { it.isWarmup }
    return RecapRowUi(
        id = recap.exercise.id,
        name = recap.exercise.exerciseNameSnapshot,
        groups = recapGroups(recap.exercise.trackingType, working),
        workingSets = working.size,
        warmups = recap.exercise.sets.size - working.size,
        delta = recapDelta(recap.best, recap.lastTime),
    )
}

internal fun recapGroups(
    trackingType: ExerciseTrackingType,
    working: List<WorkoutSet>,
): List<RecapGroup> = working.fold(emptyList()) { groups, set -> groups.withSet(trackingType, set) }

private fun List<RecapGroup>.withSet(
    trackingType: ExerciseTrackingType,
    set: WorkoutSet,
): List<RecapGroup> {
    val last = lastOrNull()
    val load = set.load
    val reps = set.reps ?: 0
    val merged: RecapGroup? =
        when {
            trackingType == ExerciseTrackingType.DURATION -> {
                (last as? RecapGroup.Timed)?.let { it.copy(seconds = it.seconds + (set.durationSeconds ?: 0)) }
            }

            load != null -> {
                (last as? RecapGroup.Loaded)
                    ?.takeIf { it.kg.compareTo(BigDecimal.valueOf(load)) == 0 }
                    ?.let { it.copy(reps = it.reps + reps) }
            }

            else -> {
                (last as? RecapGroup.Unloaded)?.let { it.copy(reps = it.reps + reps) }
            }
        }
    return if (merged != null) dropLast(1) + merged else this + newGroup(trackingType, set)
}

private fun newGroup(
    trackingType: ExerciseTrackingType,
    set: WorkoutSet,
): RecapGroup {
    val load = set.load
    return when {
        trackingType == ExerciseTrackingType.DURATION -> RecapGroup.Timed(listOf(set.durationSeconds ?: 0))
        load != null -> RecapGroup.Loaded(BigDecimal.valueOf(load), listOf(set.reps ?: 0))
        else -> RecapGroup.Unloaded(listOf(set.reps ?: 0))
    }
}

internal fun recapDelta(
    best: BestSet?,
    lastTime: BestSet?,
): RecapDelta =
    when {
        best == null -> RecapDelta.NoWorkingSets
        lastTime == null || !best.isComparableTo(lastTime) -> RecapDelta.FirstTime
        best is BestSet.Load -> RecapDelta.Load(BigDecimal.valueOf(best.kg).subtract(BigDecimal.valueOf((lastTime as BestSet.Load).kg)))
        best is BestSet.Reps -> RecapDelta.Reps(best.reps - (lastTime as BestSet.Reps).reps)
        else -> RecapDelta.Seconds((best as BestSet.Seconds).seconds - (lastTime as BestSet.Seconds).seconds)
    }
