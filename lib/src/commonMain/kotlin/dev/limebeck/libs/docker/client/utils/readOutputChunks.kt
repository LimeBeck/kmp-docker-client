package dev.limebeck.libs.docker.client.utils

import dev.limebeck.libs.docker.client.model.LogLine
import dev.limebeck.libs.docker.client.model.OutputChunk
import io.ktor.utils.io.*

internal suspend fun ByteReadChannel.readOutputChunks(
    isTty: Boolean,
    onChunk: suspend (OutputChunk) -> Unit
) {
    val buffer = ByteArray(16 * 1024)
    val header = ByteArray(8)
    while (true) {
        if (isTty) {
            val count = readAvailable(buffer)
            if (count < 0) {
                closedCause?.let { throw it }
                return
            }
            if (count > 0) onChunk(OutputChunk(LogLine.Type.UNKNOWN, buffer.copyOf(count)))
        } else {
            if (readAvailable(header, 0, 1) < 0) {
                closedCause?.let { throw it }
                return
            }
            readFully(header, 1, header.size)
            check(header[1] == 0.toByte() && header[2] == 0.toByte() && header[3] == 0.toByte()) {
                "Invalid Docker multiplex header"
            }
            val type = when (header[0].toInt()) {
                0, 1 -> LogLine.Type.STDOUT
                2 -> LogLine.Type.STDERR
                else -> error("Invalid Docker output stream: ${header[0]}")
            }
            var remaining = 0L
            for (i in 4..7) remaining = (remaining shl 8) or (header[i].toLong() and 255)
            while (remaining > 0) {
                val count = readAvailable(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                check(count >= 0) { "EOF in Docker multiplex payload ($remaining bytes missing)" }
                if (count > 0) {
                    onChunk(OutputChunk(type, buffer.copyOf(count)))
                    remaining -= count
                }
            }
        }
    }
}
