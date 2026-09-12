package dev.limebeck.libs.docker.client.utils

import dev.limebeck.libs.docker.client.model.LogLine
import io.ktor.utils.io.*

suspend fun ByteReadChannel.readLogLines(
    isTty: Boolean,
    onMessage: suspend (LogLine) -> Unit
) {
    while (!isClosedForRead) {
        val message = if (!isTty) {
            val header = ByteArray(8)
            if (readAvailable(header, 0, 1) < 0) break
            readFully(header, 1, header.size)

            check(header.sliceArray(1..3).all { it == 0.toByte() }) { "Invalid Docker multiplex header" }
            val streamType = header[0].toInt()
            check(streamType in 0..2) { "Invalid Docker output stream: $streamType" }
            val payloadSize = (
                    ((header[4].toInt() and 0xFF) shl 24) or
                            ((header[5].toInt() and 0xFF) shl 16) or
                            ((header[6].toInt() and 0xFF) shl 8) or
                            (header[7].toInt() and 0xFF)
                    )

            check(payloadSize in 0..MAX_STREAM_RECORD_SIZE) { "Docker log frame exceeds the supported size or has an invalid length" }

            val payloadBuffer = ByteArray(payloadSize)
            readFully(payloadBuffer)

            LogLine(
                line = payloadBuffer.decodeToString(),
                type = when (streamType) {
                    0 -> LogLine.Type.STDOUT // stdin is written on stdout
                    1 -> LogLine.Type.STDOUT
                    2 -> LogLine.Type.STDERR
                    else -> LogLine.Type.UNKNOWN
                }
            )
        } else {
            val line = readDockerLine() ?: break
            LogLine(
                line = line,
                type = LogLine.Type.UNKNOWN
            )
        }

        onMessage(message)
    }
    closedCause?.let { throw it }
}
