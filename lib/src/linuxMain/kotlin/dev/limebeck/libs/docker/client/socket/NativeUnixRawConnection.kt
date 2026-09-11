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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.cinterop.*
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import platform.linux.sockaddr_un
import platform.posix.*

import kotlin.concurrent.atomics.*

@OptIn(ExperimentalAtomicApi::class)
private class NativeUnixRawConnection(
    private val fd: Int,
    override val read: ByteReadChannel,
    override val write: ByteWriteChannel,
    private val scope: CoroutineScope,
    private val jobs: List<Job>,
) : DockerRawConnection {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        jobs.forEach { it.cancel() }
        scope.cancel()
        runCatching { shutdown(fd, SHUT_RDWR) }
        // Keep the descriptor allocated until both I/O jobs stop using it.
        val remaining = AtomicInt(jobs.size)
        jobs.forEach { job ->
            job.invokeOnCompletion {
                if (remaining.decrementAndFetch() == 0) runCatching { close(fd) }
            }
        }
        runCatching { (read as? ByteChannel)?.close() }
        runCatching { (write as? ByteChannel)?.close() }
    }
}

private const val SUN_PATH_LEN_LINUX = 108

@OptIn(ExperimentalForeignApi::class)
actual suspend fun openRawConnectionUnix(path: String): DockerRawConnection {
    val fd = socket(AF_UNIX, SOCK_STREAM, 0)
    if (fd < 0) error("socket(AF_UNIX) failed: errno=$errno")

    // connect(fd, sockaddr_un)
    memScoped {
        val addr = alloc<sockaddr_un>()
        addr.sun_family = AF_UNIX.convert()

        // Записываем путь в sun_path
        val bytes = path.encodeToByteArray()
        if (bytes.size >= SUN_PATH_LEN_LINUX) {
            close(fd)
            error("Unix socket path too long: $path")
        }
        // обнулим и скопируем
        for (i in 0 until SUN_PATH_LEN_LINUX) addr.sun_path[i] = 0
        bytes.forEachIndexed { i, b -> addr.sun_path[i] = b.convert() }

        val rc = connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_un>().convert())
        if (rc != 0) {
            val e = errno
            close(fd)
            error("connect($path) failed: errno=$e")
        }
    }

    val incoming = ByteChannel(autoFlush = false)
    val outgoing = ByteChannel(autoFlush = false)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val reader = scope.launch {
        val buf = ByteArray(16 * 1024)
        try {
            while (isActive) {
                val n = read(fd, buf.refTo(0), buf.size.convert()).toInt()
                if (n == 0) break // EOF
                if (n < 0) {
                    val e = errno
                    // EINTR можно продолжить
                    if (e == EINTR) continue
                    error("Unix socket read failed: errno=$e")
                }
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
        val buf = ByteArray(16 * 1024)
        try {
            while (isActive) {
                val n = outgoing.readAvailable(buf, 0, buf.size)
                if (n < 0) break
                var off = 0
                while (isActive && off < n) {
                    val w = send(fd, buf.refTo(off), (n - off).convert(), MSG_NOSIGNAL).toInt()
                    if (w < 0) {
                        val e = errno
                        if (e == EINTR) continue
                        error("Unix socket write failed: errno=$e")
                    }
                    check(w > 0) { "Unix socket write made no progress" }
                    off += w
                }
            }
        } catch (error: Throwable) {
            outgoing.close(error)
            incoming.close(error)
        } finally {
            outgoing.close()
        }
    }

    return NativeUnixRawConnection(
        fd = fd,
        read = incoming,
        write = outgoing,
        scope = scope,
        jobs = listOf(reader, writer),
    )
}
