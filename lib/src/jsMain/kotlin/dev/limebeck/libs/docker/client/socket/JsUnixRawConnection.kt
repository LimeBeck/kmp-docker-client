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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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
    fun pause(): NetSocket
    fun resume(): NetSocket
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

    val chunks = Channel<ByteArray>(1)
    socket.on("data") { chunk ->
        socket.pause()
        val bytes = uint8ArrayToByteArray(chunk.unsafeCast<Uint8Array>())
        if (chunks.trySend(bytes).isFailure) {
            val error = IllegalStateException("Socket delivered data while paused")
            chunks.close(error)
            incoming.close(error)
            socket.destroy()
        }
    }
    socket.on("end") { chunks.close() }
    socket.on("error") { err ->
        val error = RuntimeException(err?.toString() ?: "socket error")
        chunks.close(error)
        incoming.close(error)
        outgoing.close(error)
    }
    val readerJob = scope.launch {
        try {
            for (bytes in chunks) {
                incoming.writeFully(bytes)
                incoming.flush()
                socket.resume()
            }
        } catch (error: Throwable) {
            incoming.close(error)
        } finally {
            incoming.close()
            chunks.cancel()
        }
    }

    // Writer: ByteChannel -> socket.write(Uint8Array)
    val writerJob = scope.launch {
        val buf = ByteArray(16 * 1024)
        try {
            while (isActive) {
                val n = outgoing.readAvailable(buf, 0, buf.size)
                if (n < 0) break
                if (n == 0) continue
                suspendCancellableCoroutine<Unit> { continuation ->
                    socket.write(byteArrayToUint8Array(buf, n)) { continuation.resume(Unit) }
                }
            }
        } catch (error: Throwable) {
            outgoing.close(error)
            incoming.close(error)
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
        jobs = listOf(readerJob, writerJob),
    )
}
