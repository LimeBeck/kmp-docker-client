package dev.limebeck.libs.docker.client.utils

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.contracts.ExperimentalContracts

data class HijackHandshake(
    val status: Int,
    val leftover: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HijackHandshake

        if (status != other.status) return false
        if (!leftover.contentEquals(other.leftover)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + leftover.contentHashCode()
        return result
    }
}

fun buildHttpHeader(
    method: HttpMethod,
    path: String,
    parameters: Parameters = Parameters.Empty,
    headers: Map<String, String> = emptyMap()
) = buildString {
    val url = listOfNotNull(path, parameters.formUrlEncode().takeIf { it.isNotEmpty() }).joinToString("?")
    append("${method.value} $url HTTP/1.1\r\n")
    headers.forEach { (k, v) ->
        append("$k: $v\r\n")
    }
    append("\r\n")
}

@OptIn(ExperimentalContracts::class)
val HijackHandshake.isError: Boolean
    get() = status != 101 && status != 200

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

            DockerClient.logger.debug { "Hijack response: HTTP $status" }
            return HijackHandshake(status, leftover)
        }
    }
}

internal suspend fun <T> withPrefixedChannel(
    leftover: ByteArray,
    upstream: ByteReadChannel,
    block: suspend (ByteReadChannel) -> T
): T = coroutineScope {
    if (leftover.isEmpty()) return@coroutineScope block(upstream)
    val out = ByteChannel(autoFlush = true)
    val forwarder = launch {
        try {
            out.writeFully(leftover)
            upstream.copyTo(out)
            out.close(upstream.closedCause)
        } catch (error: Throwable) {
            out.close(error)
        }
    }
    try {
        block(out)
    } finally {
        forwarder.cancel()
        out.cancel()
    }
}

suspend fun DockerClient.createInteractiveSession(
    tty: Boolean,
    method: HttpMethod,
    path: String,
    parameters: Parameters = Parameters.Empty,
    headers: Map<String, String> = emptyMap(),
    body: ByteArray? = null
): Result<ExecSession, ErrorResponse> = coroutineScope {
    val conn = openRawConnection()

    val headers = buildHttpHeader(
        method = method,
        path = apiPath(path),
        parameters = parameters,
        headers = headers + buildMap {
            body?.let { set("Content-Length", body.size.toString()) }
        }
    )

    try {
        conn.write.writeFully(headers.encodeToByteArray())
        body?.let { conn.write.writeFully(it) }
        conn.write.flush()

        DockerClient.logger.debug { "Send hijack request: ${method.value} ${apiPath(path)}" }

        val hs = readHttp11Headers(conn.read)

        if (hs.isError) {
            conn.close()
            return@coroutineScope ErrorResponse("Docker hijack failed: HTTP ${hs.status}").asError()
        }

        DockerClient.logger.debug { "Connection hjacked" }

        val incomingFlow: Flow<LogLine> = flow {
            withPrefixedChannel(hs.leftover, conn.read) { channel ->
                channel.readLogLines(tty) { emit(it) }
            }
        }
        val chunks: Flow<OutputChunk> = flow {
            withPrefixedChannel(hs.leftover, conn.read) { channel ->
                channel.readOutputChunks(tty) { emit(it) }
            }
        }

        val session = ExecSession(incomingFlow, tty, conn, chunks)

        return@coroutineScope session.asSuccess()
    } catch (t: Throwable) {
        runCatching { conn.close() }
        if (t is CancellationException) throw t
        return@coroutineScope ErrorResponse(message = t.message ?: "createInteractiveSession failed").asError()
    }
}
