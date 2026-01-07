package dev.limebeck.docker.client.model

import kotlin.jvm.JvmInline

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
        fun <T> success(value: T): Result<T, Nothing> = Result(value)

        // Wrap error in Failure marker
        fun <E> error(value: E): Result<Nothing, E> = Result(Failure(value))
    }

    // Check what's inside: Failure marker or data
    val isSuccess: Boolean get() = unboxed !is Failure
    val isError: Boolean get() = unboxed is Failure

    @Suppress("UNCHECKED_CAST")
    fun getOrNull(): T? = if (isSuccess) unboxed as T else null

    @Suppress("UNCHECKED_CAST")
    fun errorOrNull(): E? = if (isError) (unboxed as Failure).error as E else null

    @Suppress("UNCHECKED_CAST")
    fun getOrThrow(): T {
        if (isSuccess) return unboxed as T
        throw IllegalStateException("Result is an error: ${errorOrNull()}")
    }

    override fun toString(): String = if (isSuccess) "Success($unboxed)" else "Error(${errorOrNull()})"

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

    inline fun <R> map(transform: (T) -> R): Result<R, E> {
        return if (isSuccess) {
            @Suppress("UNCHECKED_CAST")
            success(transform(unboxed as T))
        } else {
            @Suppress("UNCHECKED_CAST")
            Result(unboxed) // Just pass the error through without repacking
        }
    }

    inline fun <R> mapError(transform: (E) -> R): Result<T, R> {
        return if (isError) {
            @Suppress("UNCHECKED_CAST")
            Result.error(transform((unboxed as Failure).error as E))
        } else {
            @Suppress("UNCHECKED_CAST")
            Result(unboxed)
        }
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T, E> {
        if (isSuccess) {
            @Suppress("UNCHECKED_CAST")
            action(unboxed as T)
        }
        return this
    }

    inline fun onError(action: (E) -> Unit): Result<T, E> {
        if (isError) {
            @Suppress("UNCHECKED_CAST")
            action((unboxed as Failure).error as E)
        }
        return this
    }
}

// Extension functions remain but refer to a companion
fun <T> T.asSuccess(): Result<T, Nothing> = Result.success(this)
fun <E> E.asError(): Result<Nothing, E> = Result.error(this)
