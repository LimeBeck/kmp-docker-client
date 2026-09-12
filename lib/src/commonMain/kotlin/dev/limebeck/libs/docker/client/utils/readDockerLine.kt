package dev.limebeck.libs.docker.client.utils

import io.ktor.utils.io.*
import kotlinx.io.EOFException

internal const val MAX_STREAM_RECORD_SIZE = 1024 * 1024

/** Preserve legacy CR/LF handling and a final unterminated record, while bounding memory. */
internal suspend fun ByteReadChannel.readDockerLine(): String? {
    val text = StringBuilder()
    val count = try {
        readLineStrictTo(text, limit = MAX_STREAM_RECORD_SIZE.toLong(), lineEnding = LineEnding.Lenient)
    } catch (error: EOFException) {
        closedCause?.let { throw it }
        if (!isClosedForRead) throw error
        if (text.isEmpty()) -1L else text.length.toLong()
    }
    if (count < 0) {
        closedCause?.let { throw it }
        return null
    }
    return text.toString()
}
