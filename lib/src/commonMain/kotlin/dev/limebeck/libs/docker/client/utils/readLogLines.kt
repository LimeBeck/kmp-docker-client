package dev.limebeck.libs.docker.client.utils

import dev.limebeck.libs.docker.client.model.LogLine
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.FlowCollector

suspend fun ByteReadChannel.readLogLines(
    isTty: Boolean,
    collector: FlowCollector<LogLine>
) {
    while (!isClosedForRead) {
        val message = if (!isTty) {
            val header = ByteArray(8)
            try {
                readFully(header)
            } catch (e: Throwable) {
                break
            }

            val streamType = header[0].toInt()
            val payloadSize = (
                    ((header[4].toInt() and 0xFF) shl 24) or
                            ((header[5].toInt() and 0xFF) shl 16) or
                            ((header[6].toInt() and 0xFF) shl 8) or
                            (header[7].toInt() and 0xFF)
                    )

            if (payloadSize < 0) break

            val payloadBuffer = ByteArray(payloadSize)
            try {
                readFully(payloadBuffer)
            } catch (e: Throwable) {
                break
            }

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
            val line = try {
                readUTF8Line()
            } catch (e: Throwable) {
                null
            } ?: break
            LogLine(
                line = line,
                type = LogLine.Type.UNKNOWN
            )
        }

        collector.emit(message)
    }
}
