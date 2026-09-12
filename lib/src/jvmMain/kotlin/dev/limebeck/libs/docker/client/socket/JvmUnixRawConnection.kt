package dev.limebeck.libs.docker.client.socket

import io.ktor.utils.io.close
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnixDomainSocketAddress
import java.net.StandardProtocolFamily
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

private class JvmUnixRawConnection(
    private val socketChannel: SocketChannel,
    override val read: ByteReadChannel,
    override val write: ByteWriteChannel,
    private val scope: CoroutineScope,
    private val jobs: List<Job>,
) : DockerRawConnection {
    override fun close() {
        jobs.forEach { it.cancel() }
        scope.cancel()
        runCatching { socketChannel.close() }
        runCatching { (read as? ByteChannel)?.close() }
        runCatching { (write as? ByteChannel)?.close() }
    }
}

private const val DEFAULT_BUF_SIZE = 16 * 1024 // 16 KiB

actual suspend fun openRawConnectionUnix(path: String): DockerRawConnection {
    val address = UnixDomainSocketAddress.of(path)
    val socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)
    try {
        withContext(Dispatchers.IO) { socketChannel.connect(address) }
    } catch (error: Throwable) {
        socketChannel.close()
        throw error
    }

    val incoming = ByteChannel(autoFlush = false)
    val outgoing = ByteChannel(autoFlush = false)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val reader = scope.launch {
        val buf = ByteArray(DEFAULT_BUF_SIZE)
        try {
            while (isActive) {
                val n = socketChannel.read(ByteBuffer.wrap(buf))
                if (n < 0) break
                incoming.writeFully(buf, 0, n)
                incoming.flush()
            }
        } catch (error: Throwable) {
            incoming.close(error)
        } finally {
            incoming.close()
        }
    }

    val writer = scope.launch {
        val buf = ByteArray(DEFAULT_BUF_SIZE)
        try {
            while (isActive) {
                val n = outgoing.readAvailable(buf, 0, buf.size)
                if (n < 0) break
                val bytes = ByteBuffer.wrap(buf, 0, n)
                while (bytes.hasRemaining()) socketChannel.write(bytes)
            }
        } catch (error: Throwable) {
            outgoing.close(error)
            incoming.close(error)
            socketChannel.close()
        } finally {
            outgoing.close()
        }
    }

    return JvmUnixRawConnection(
        socketChannel = socketChannel,
        read = incoming,
        write = outgoing,
        scope = scope,
        jobs = listOf(reader, writer),
    )
}
