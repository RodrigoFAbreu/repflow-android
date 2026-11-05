package com.repflow.app.application.workout

/**
 * The store couldn't be read: [WorkoutRepository.findById] and
 * [WorkoutRepository.findActiveSession] throw this rather than a storage type,
 * which the layers calling them may not name. It stands for a transient fault;
 * a row that can't be mapped is a data fault and stays a different exception.
 */
class WorkoutReadFailedException(
    cause: Throwable,
) : RuntimeException("The workout store could not be read", cause)
