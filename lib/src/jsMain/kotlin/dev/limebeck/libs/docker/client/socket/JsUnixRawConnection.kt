package dev.limebeck.libs.docker.client.socket

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.js.json

@JsModule("net")
@JsNonModule
private external object Net {
    fun createConnection(options: dynamic): NetSocket
}

private external interface NetSocket {
    fun on(event: String, cb: (arg: dynamic) -> Unit): NetSocket
    fun write(data: dynamic, cb: (() -> Unit)? = definedExternally): Boolean
    fun end()
    fun destroy()
}

private class JsUnixRawConnection(
    private val socket: NetSocket,
    override val read: ByteReadChannel,
    override val write: ByteWriteChannel,
    private val scope: CoroutineScope,
    private val jobs: List<Job>,
) : DockerRawConnection {
    override fun close() {
        jobs.forEach { it.cancel() }
        scope.cancel()
        runCatching { socket.destroy() }
        runCatching { (read as? ByteChannel)?.close() }
        runCatching { (write as? ByteChannel)?.close() }
    }
}

private fun uint8ArrayToByteArray(u8: Uint8Array): ByteArray {
    val out = ByteArray(u8.length)
    for (i in 0 until u8.length) out[i] = u8[i]
    return out
}

private fun byteArrayToUint8Array(bytes: ByteArray, len: Int): Uint8Array {
    val u8 = Uint8Array(len)
    for (i in 0 until len) u8[i] = bytes[i]
    return u8
}

actual suspend fun openRawConnectionUnix(path: String): DockerRawConnection {
    val socket = Net.createConnection(json("path" to path))

    val incoming = ByteChannel(autoFlush = false)
    val outgoing = ByteChannel(autoFlush = false)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    socket.on("connect") {
        console.log("Connected to docker unix socket:", path)
    }

    // Reader: data(Buffer|Uint8Array) -> ByteChannel (через coroutine, потому что writeFully suspend)
    socket.on("data") { chunk ->
        val u8 = chunk.unsafeCast<Uint8Array>() // Node Buffer обычно совместим с Uint8Array
        val bytes = uint8ArrayToByteArray(u8)
        scope.launch {
            incoming.writeFully(bytes)
            incoming.flush()
        }
    }

    socket.on("end") {
        incoming.close()
    }

    socket.on("error") { err ->
        incoming.close(RuntimeException(err?.toString() ?: "socket error"))
    }

    // Writer: ByteChannel -> socket.write(Uint8Array)
    val writerJob = scope.launch {
        val buf = ByteArray(16 * 1024)
        try {
            while (isActive) {
                val n = outgoing.readAvailable(buf, 0, buf.size)
                if (n < 0) break
                if (n == 0) continue
                socket.write(byteArrayToUint8Array(buf, n))
            }
        } finally {
            runCatching { socket.end() }
            outgoing.close()
        }
    }

    return JsUnixRawConnection(
        socket = socket,
        read = incoming,
        write = outgoing,
        scope = scope,
        jobs = listOf(writerJob),
    )
}