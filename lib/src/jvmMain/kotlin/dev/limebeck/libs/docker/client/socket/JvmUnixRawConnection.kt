package dev.limebeck.libs.docker.client.socket

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
import java.io.InputStream
import java.io.OutputStream
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
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
    val socketChannel = withContext(Dispatchers.IO) {
        SocketChannel.open(address)
    }

    val input: InputStream = Channels.newInputStream(socketChannel)
    val output: OutputStream = Channels.newOutputStream(socketChannel)

    // Прокидываем blocking streams в ktor ByteChannels (suspend-friendly)
    val incoming = ByteChannel(autoFlush = false)
    val outgoing = ByteChannel(autoFlush = false)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val reader = scope.launch {
        val buf = ByteArray(DEFAULT_BUF_SIZE)
        try {
            while (isActive) {
                val n = input.read(buf)
                if (n < 0) break
                incoming.writeFully(buf, 0, n)
                incoming.flush()
            }
        } catch (_: Throwable) {
            // ignore; close below
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
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (_: Throwable) {
            // ignore
        } finally {
            runCatching { output.flush() }
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
