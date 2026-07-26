package com.repflow.app.domain.common

/**
 * Explicit success/failure result for expected domain and application
 * outcomes (validation failures, not-found, persistence errors, ...).
 *
 * This is deliberately not Kotlin's [kotlin.Result], which is modelled around
 * [Throwable] and encourages exceptions for expected, recoverable outcomes.
 * It is also not an Arrow `Either` - no such dependency is introduced for a
 * two-case result type this small.
 */
sealed interface DomainResult<out T, out E> {
    data class Success<out T>(
        val value: T,
    ) : DomainResult<T, Nothing>

    data class Failure<out E>(
        val error: E,
    ) : DomainResult<Nothing, E>
}

inline fun <T, E, R> DomainResult<T, E>.map(transform: (T) -> R): DomainResult<R, E> =
    when (this) {
        is DomainResult.Success -> DomainResult.Success(transform(value))
        is DomainResult.Failure -> this
    }

inline fun <T, E, F> DomainResult<T, E>.mapFailure(transform: (E) -> F): DomainResult<T, F> =
    when (this) {
        is DomainResult.Success -> this
        is DomainResult.Failure -> DomainResult.Failure(transform(error))
    }

inline fun <T, E, R> DomainResult<T, E>.fold(
    onSuccess: (T) -> R,
    onFailure: (E) -> R,
): R =
    when (this) {
        is DomainResult.Success -> onSuccess(value)
        is DomainResult.Failure -> onFailure(error)
    }

fun <T, E> DomainResult<T, E>.getOrNull(): T? =
    when (this) {
        is DomainResult.Success -> value
        is DomainResult.Failure -> null
    }

/**
 * Returns the success value, or the result of [onFailure] for a failure -
 * typically a lambda ending in a non-local `return`, so call sites read as a
 * guard clause (`val x = f().getOrElse { return ... }`) rather than a nested
 * `when`.
 */
inline fun <T, E> DomainResult<T, E>.getOrElse(onFailure: (E) -> T): T =
    when (this) {
        is DomainResult.Success -> value
        is DomainResult.Failure -> onFailure(error)
    }

val DomainResult<*, *>.isSuccess: Boolean get() = this is DomainResult.Success

val DomainResult<*, *>.isFailure: Boolean get() = this is DomainResult.Failure
