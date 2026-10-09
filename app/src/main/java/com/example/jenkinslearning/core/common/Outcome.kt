package com.example.jenkinslearning.core.common

import com.example.jenkinslearning.domain.error.JenkinsError

/**
 * Result of a repository or use case call. A dedicated type (rather than
 * kotlin.Result) keeps errors typed: callers get a [JenkinsError], never a Throwable.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: JenkinsError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (JenkinsError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}
