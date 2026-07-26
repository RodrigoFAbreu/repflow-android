package com.repflow.app.application.exercise

import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseName
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveExercises
    @Inject
    constructor(
        private val repository: ExerciseRepository,
    ) {
        /**
         * [rawQuery] is normalized through the same pipeline as a persisted
         * name (see [ExerciseName]) before criteria are built, so the
         * repository only ever compares already-normalized keys.
         */
        operator fun invoke(
            status: ExerciseStatusFilter,
            rawQuery: String,
        ): Flow<List<Exercise>> {
            val normalizedQuery =
                if (rawQuery.isBlank()) {
                    ""
                } else {
                    ExerciseName.keyOf(ExerciseName.cleanDisplay(rawQuery))
                }
            return repository.observe(ExerciseQueryCriteria(status = status, normalizedQuery = normalizedQuery))
        }
    }
