package com.repflow.app.application.history

import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * The finished-workout summary (remediation-1 CP9, `4a` `nDone`): the session
 * itself, a recap per exercise against the last time it was trained, and the
 * sets that beat every earlier one.
 *
 * Everything here is derived from the completed-session history, never
 * stored, the way [ObserveRecentTraining] derives Home's load increases.
 *
 * @property recaps one per [WorkoutSession.exercises] entry, in session order.
 * @property personalBests one per exercise whose best working set this
 *   session beat every earlier valid working set of it - see [BestSet].
 */
data class WorkoutSummary(
    val session: WorkoutSession,
    val recaps: List<ExerciseRecap>,
    val personalBests: List<PersonalBest>,
)

/**
 * One exercise of the finished workout.
 *
 * @property best this session's best working set of it; `null` when it has
 *   no working set (nothing logged, or warm-ups only).
 * @property lastTime the best working set of the most recent **earlier**
 *   valid session that has a comparable one ([BestSet.isComparableTo]);
 *   `null` when there is none - the first time it is trained, or the last time
 *   was measured differently.
 */
data class ExerciseRecap(
    val exercise: WorkoutExercise,
    val best: BestSet?,
    val lastTime: BestSet?,
)

/**
 * A best set this session that is better than every earlier valid one
 * ([previous], set on [previousEndedAt]) - `4a`'s "Best set on …" card.
 * The first time an exercise is ever trained there is nothing to beat, so it
 * is never a best set.
 */
data class PersonalBest(
    val exerciseId: ExerciseId,
    val exerciseName: String,
    val best: BestSet,
    val previous: BestSet,
    val previousEndedAt: Instant,
)

/**
 * The single figure a set list is judged by, chosen from what its tracking
 * type records - working sets only:
 *
 * - [Load]: a weight-and-reps exercise with any loaded set - the heaviest
 *   load, and the most reps done at it;
 * - [Reps]: reps only, or a weight-and-reps exercise logged without a load -
 *   the most reps in one set;
 * - [Seconds]: a timed hold - the longest one.
 *
 * Two best sets are only ever compared when they are the same kind: a
 * figure measured another way says nothing about this one.
 */
sealed interface BestSet : Comparable<BestSet> {
    data class Load(
        val kg: Double,
        val reps: Int,
    ) : BestSet

    data class Reps(
        val reps: Int,
    ) : BestSet

    data class Seconds(
        val seconds: Int,
    ) : BestSet

    fun isComparableTo(other: BestSet): Boolean = this::class == other::class

    /** Heavier first, then more reps at that load; more reps; longer. Only defined between comparable sets. */
    override fun compareTo(other: BestSet): Int {
        require(isComparableTo(other)) { "$this is not comparable to $other" }
        return when (this) {
            is Load -> compareValuesBy(this, other as Load, { it.kg }, { it.reps })
            is Reps -> reps.compareTo((other as Reps).reps)
            is Seconds -> seconds.compareTo((other as Seconds).seconds)
        }
    }
}

class ObserveWorkoutSummary
    @Inject
    constructor(
        private val repository: WorkoutRepository,
    ) {
        /** Emits the summary of the completed session [sessionId], or `null` while no completed session has that id. */
        operator fun invoke(sessionId: WorkoutSessionId): Flow<WorkoutSummary?> =
            repository
                .observeCompletedSessions(includeInvalidated = true)
                .map { sessions -> workoutSummaryOf(sessionId, sessions) }
                .distinctUntilChanged()
    }

/**
 * **Only valid sessions are compared against.** An invalidated session is left
 * out as it is left out of progression and of Home's load increases, and only
 * sessions that ended before this one count as "earlier".
 */
internal fun workoutSummaryOf(
    sessionId: WorkoutSessionId,
    sessions: List<WorkoutSession>,
): WorkoutSummary? {
    val session =
        sessions.find { it.id == sessionId && it.status == WorkoutSessionStatus.COMPLETED } ?: return null
    val endedAt = session.endedAt ?: return null
    val earlierNewestFirst =
        sessions
            .filter { it.id != sessionId && it.invalidatedAt == null && it.endedAt?.isBefore(endedAt) == true }
            .sortedByDescending { it.endedAt }
    val recaps =
        session.exercises.map { exercise ->
            val best = bestSetOf(exercise.trackingType, exercise.sets)
            ExerciseRecap(
                exercise = exercise,
                best = best,
                lastTime = best?.let { lastTimeOf(exercise.exerciseId, it, earlierNewestFirst) },
            )
        }
    return WorkoutSummary(
        session = session,
        recaps = recaps,
        personalBests = personalBestsOf(session, earlierNewestFirst),
    )
}

private fun lastTimeOf(
    exerciseId: ExerciseId,
    best: BestSet,
    earlierNewestFirst: List<WorkoutSession>,
): BestSet? =
    earlierNewestFirst.firstNotNullOfOrNull { earlier ->
        bestSetIn(earlier, exerciseId)?.takeIf { it.isComparableTo(best) }
    }

private fun personalBestsOf(
    session: WorkoutSession,
    earlierNewestFirst: List<WorkoutSession>,
): List<PersonalBest> =
    session.exercises
        .map { it.exerciseId }
        .distinct()
        .mapNotNull { exerciseId ->
            val best = bestSetIn(session, exerciseId) ?: return@mapNotNull null
            // Newest first, so a tie keeps the most recent session that set it.
            val previous =
                earlierNewestFirst
                    .mapNotNull { earlier -> bestSetIn(earlier, exerciseId)?.takeIf { it.isComparableTo(best) }?.let { earlier to it } }
                    .reduceOrNull { kept, next -> if (next.second > kept.second) next else kept }
                    ?: return@mapNotNull null
            if (best <= previous.second) return@mapNotNull null
            PersonalBest(
                exerciseId = exerciseId,
                exerciseName = session.exercises.first { it.exerciseId == exerciseId }.exerciseNameSnapshot,
                best = best,
                previous = previous.second,
                previousEndedAt = checkNotNull(previous.first.endedAt),
            )
        }

/** An exercise may appear more than once in a session; its best set is over every entry of it. */
private fun bestSetIn(
    session: WorkoutSession,
    exerciseId: ExerciseId,
): BestSet? {
    val entries = session.exercises.filter { it.exerciseId == exerciseId }
    if (entries.isEmpty()) return null
    return bestSetOf(entries.first().trackingType, entries.flatMap { it.sets })
}

internal fun bestSetOf(
    trackingType: ExerciseTrackingType,
    sets: List<WorkoutSet>,
): BestSet? {
    val working = sets.filterNot { it.isWarmup }
    if (working.isEmpty()) return null
    return when (trackingType) {
        ExerciseTrackingType.DURATION -> {
            working.mapNotNull { it.durationSeconds }.maxOrNull()?.let { BestSet.Seconds(it) }
        }

        ExerciseTrackingType.WEIGHT_AND_REPS, ExerciseTrackingType.REPS_ONLY -> {
            val heaviest = working.mapNotNull { it.load }.maxOrNull()
            if (heaviest != null) {
                BestSet.Load(heaviest, working.filter { it.load == heaviest }.maxOf { it.reps ?: 0 })
            } else {
                working.mapNotNull { it.reps }.maxOrNull()?.let { BestSet.Reps(it) }
            }
        }
    }
}

/**
 * The sessions in [sessions] that set a personal best - History's `PR` badge
 * (remediation-1 CP12): exactly the valid completed sessions whose
 * [workoutSummaryOf] lists at least one [PersonalBest], worked out in one pass
 * over the history instead of one summary per row.
 *
 * Sessions are visited oldest first, keeping each exercise's best set so far
 * per kind ([BestSet.isComparableTo]); a session sets a personal best when one
 * of its exercises beats the best of the same kind from every session that
 * ended **before** it. Sessions that ended at the same instant are judged
 * against the same earlier bests, as [workoutSummaryOf] judges them. An
 * invalidated session neither earns the badge nor counts as an earlier one.
 */
fun sessionsWithPersonalBests(sessions: List<WorkoutSession>): Set<WorkoutSessionId> {
    val bestSoFar = mutableMapOf<ExerciseId, MutableMap<KClass<out BestSet>, BestSet>>()
    val withPersonalBests = mutableSetOf<WorkoutSessionId>()
    sessions
        .filter { it.status == WorkoutSessionStatus.COMPLETED && it.invalidatedAt == null && it.endedAt != null }
        .groupBy { checkNotNull(it.endedAt) }
        .toSortedMap()
        .values
        .forEach { endedTogether ->
            val bestsBySession = endedTogether.associate { session -> session.id to bestSetsByExercise(session) }
            withPersonalBests += bestsBySession.filterValues { bests -> bests.beatsAny(bestSoFar) }.keys
            bestsBySession.values.forEach { bests -> bestSoFar.keepBests(bests) }
        }
    return withPersonalBests
}

/** Whether any of these bests beats the earlier best of the same kind for its exercise. */
private fun Map<ExerciseId, BestSet>.beatsAny(bestSoFar: Map<ExerciseId, Map<KClass<out BestSet>, BestSet>>): Boolean =
    any { (exerciseId, best) ->
        val previous = bestSoFar[exerciseId]?.get(best::class)
        previous != null && best > previous
    }

private fun MutableMap<ExerciseId, MutableMap<KClass<out BestSet>, BestSet>>.keepBests(bests: Map<ExerciseId, BestSet>) {
    bests.forEach { (exerciseId, best) ->
        val byKind = getOrPut(exerciseId) { mutableMapOf() }
        val kept = byKind[best::class]
        if (kept == null || best > kept) byKind[best::class] = best
    }
}

private fun bestSetsByExercise(session: WorkoutSession): Map<ExerciseId, BestSet> =
    session.exercises
        .map { it.exerciseId }
        .distinct()
        .mapNotNull { exerciseId -> bestSetIn(session, exerciseId)?.let { exerciseId to it } }
        .toMap()
