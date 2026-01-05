package dev.limebeck.docker.client.model

import kotlin.jvm.JvmInline

@JvmInline
value class Result<out T, out E>(
    @PublishedApi internal val unboxed: Any?
) {
    // Вспомогательный класс-маркер для ошибки.
    // Он нужен, чтобы отличить Success<String> от Error<String>.
    class Failure(val error: Any?) {
        override fun toString() = "Failure($error)"
    }

    companion object {
        fun <T> success(value: T): Result<T, Nothing> = Result(value)

        // Оборачиваем ошибку в маркер Failure
        fun <E> error(value: E): Result<Nothing, E> = Result(Failure(value))
    }

    // Проверяем, что внутри: маркер Failure или данные
    val isSuccess: Boolean get() = unboxed !is Failure
    val isError: Boolean get() = unboxed is Failure

    @Suppress("UNCHECKED_CAST")
    fun getOrNull(): T? = if (isSuccess) unboxed as T else null

    @Suppress("UNCHECKED_CAST")
    fun errorOrNull(): E? = if (isError) (unboxed as Failure).error as E else null

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
            Result.success(transform(unboxed as T))
        } else {
            @Suppress("UNCHECKED_CAST")
            Result(unboxed) // Просто перекидываем ошибку без перепаковки
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

// Extension-функции остаются, но ссылаются на компаньон
fun <T> T.asSuccess(): Result<T, Nothing> = Result.success(this)
fun <E> E.asError(): Result<Nothing, E> = Result.error(this)
