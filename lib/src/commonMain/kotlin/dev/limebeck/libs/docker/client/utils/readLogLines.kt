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

            val streamType = header[0].toInt()
            val payloadSize = (
                    ((header[4].toInt() and 0xFF) shl 24) or
                            ((header[5].toInt() and 0xFF) shl 16) or
                            ((header[6].toInt() and 0xFF) shl 8) or
                            (header[7].toInt() and 0xFF)
                    )

            check(payloadSize >= 0) { "Invalid Docker multiplex payload size" }

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
            val line = readLine() ?: break
            LogLine(
                line = line,
                type = LogLine.Type.UNKNOWN
            )
        }

        onMessage(message)
    }
    closedCause?.let { throw it }
}
