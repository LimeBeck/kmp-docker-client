package dev.limebeck.libs.docker.client.model

import kotlin.jvm.JvmInline

/**
 * Success value [T] or typed error [E], distinct from kotlin.Result.
 *
 * Callback exceptions propagate unchanged; this wrapper does not catch transport exceptions.
 * Use [fold] to handle both branches or [getOrThrow] when a typed error should abort the operation.
 */
@JvmInline
value class Result<out T, out E>(
    @PublishedApi internal val unboxed: Any?
) {
    // Helper marker class for errors.
    // It is necessary to distinguish Success<String> from Error<String>.
    class Failure(val error: Any?) {
        override fun toString() = "Failure($error)"
    }

    companion object {
        fun <T> success(value: T): Result<T, Nothing> =
            Result(value)

        // Wrap error in Failure marker
        fun <E> error(value: E): Result<Nothing, E> =
            Result(Failure(value))
    }

    // Check what's inside: Failure marker or data
    val isSuccess: Boolean get() = unboxed !is Failure
    val isError: Boolean get() = unboxed is Failure

    /**
     * Returns the success value, or null on error. A successful nullable value can also be null.
     */
    @Suppress("UNCHECKED_CAST")
    fun getOrNull(): T? = if (isSuccess) unboxed as T else null

    /**
     * Returns the typed error, or null on success. A nullable error can also be null.
     */
    @Suppress("UNCHECKED_CAST")
    fun errorOrNull(): E? = if (isError) (unboxed as Failure).error as E else null

    /**
     * Returns the success value.
     * @throws IllegalStateException If this result contains an error.
     */
    @Suppress("UNCHECKED_CAST")
    fun getOrThrow(): T {
        if (isSuccess) return unboxed as T
        throw IllegalStateException("Result is an error: ${errorOrNull()}")
    }

    override fun toString(): String = if (isSuccess) "Success($unboxed)" else "Error(${errorOrNull()})"

    /**
     * Invokes exactly one branch and returns its value. Callback exceptions propagate.
     * @param onSuccess Handler for the success value.
     * @param onError Handler for the typed error.
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onError: (E) -> R
    ): R {
        return if (isSuccess) {
            @Suppress("UNCHECKED_CAST")
            onSuccess(unboxed as T)
        } else {
            @Suppress("UNCHECKED_CAST")
            onError((unboxed as Failure).error as E)
        }
    }

    /**
     * Transforms the success value, preserving errors unchanged. Transform exceptions propagate.
     */
    inline fun <R> map(transform: (T) -> R): Result<R, E> {
        return if (isSuccess) {
            @Suppress("UNCHECKED_CAST")
            (success(transform(unboxed as T)))
        } else {
            @Suppress("UNCHECKED_CAST")
            (Result(unboxed)) // Just pass the error through without repacking
        }
    }

    /**
     * Transforms the error value, preserving success unchanged. Transform exceptions propagate.
     */
    inline fun <R> mapError(transform: (E) -> R): Result<T, R> {
        return if (isError) {
            @Suppress("UNCHECKED_CAST")
            (error(transform((unboxed as Failure).error as E)))
        } else {
            @Suppress("UNCHECKED_CAST")
            (Result(unboxed))
        }
    }

    /**
     * Runs [action] only on success and returns this result unchanged. Action exceptions propagate.
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T, E> {
        if (isSuccess) {
            @Suppress("UNCHECKED_CAST")
            action(unboxed as T)
        }
        return this
    }

    /**
     * Runs [action] only on error and returns this result unchanged. Action exceptions propagate.
     */
    inline fun onError(action: (E) -> Unit): Result<T, E> {
        if (isError) {
            @Suppress("UNCHECKED_CAST")
            action((unboxed as Failure).error as E)
        }
        return this
    }
}

// Extension functions remain but refer to a companion
fun <T> T.asSuccess(): Result<T, Nothing> =
    Result.success(this)

fun <E> E.asError(): Result<Nothing, E> =
    Result.error(this)
