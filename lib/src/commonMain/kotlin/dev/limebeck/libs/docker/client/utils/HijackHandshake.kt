package dev.limebeck.libs.docker.client.utils

import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class HijackHandshake(
    val status: Int,
    val leftover: ByteArray
)

suspend fun readHttp11Headers(channel: ByteReadChannel): HijackHandshake {
    val buf = ByteArray(8 * 1024)
    val acc = ArrayList<Byte>(8 * 1024)

    fun findHeaderEnd(): Int {
        // ищем \r\n\r\n
        for (i in 0..acc.size - 4) {
            if (acc[i] == '\r'.code.toByte() &&
                acc[i + 1] == '\n'.code.toByte() &&
                acc[i + 2] == '\r'.code.toByte() &&
                acc[i + 3] == '\n'.code.toByte()
            ) return i
        }
        return -1
    }

    while (true) {
        val n = channel.readAvailable(buf, 0, buf.size)
        if (n <= 0) error("EOF while reading HTTP headers")

        for (i in 0 until n) acc.add(buf[i])

        val endIdx = findHeaderEnd()
        if (endIdx >= 0) {
            val headerLen = endIdx + 4
            val headerBytes = ByteArray(headerLen) { acc[it] }
            val leftover = ByteArray(acc.size - headerLen) { acc[headerLen + it] }

            val headerText = headerBytes.decodeToString()
            val statusLine = headerText.lineSequence().firstOrNull()
                ?: error("Bad HTTP response: empty status line")

            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
                ?: error("Bad HTTP status line: $statusLine")

            return HijackHandshake(status, leftover)
        }
    }
}

fun prependLeftover(
    leftover: ByteArray,
    upstream: ByteReadChannel
): ByteReadChannel {
    if (leftover.isEmpty()) return upstream

    val out = ByteChannel(autoFlush = false)
    CoroutineScope(Dispatchers.Default).launch {
        try {
            out.writeFully(leftover)
            out.flush()
            // прокидываем остаток потока
            upstream.copyTo(out)
        } finally {
            out.close()
        }
    }
    return out
}
