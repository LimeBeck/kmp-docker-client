package dev.limebeck.libs.docker.client.socket

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

private class NativeUnixRawConnection(
    private val fd: Int,
    override val read: ByteReadChannel,
    override val write: ByteWriteChannel,
    private val scope: CoroutineScope,
    private val jobs: List<Job>,
) : DockerRawConnection {
    override fun close() {
        jobs.forEach { it.cancel() }
        scope.cancel()
        runCatching { close(fd) }
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
                    break
                }
                incoming.writeFully(buf, 0, n)
                incoming.flush()
            }
        } catch (_: Throwable) {
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
                while (off < n) {
                    val w = write(fd, buf.refTo(off), (n - off).convert()).toInt()
                    if (w < 0) {
                        val e = errno
                        if (e == EINTR) continue
                        // EPIPE = peer closed
                        break
                    }
                    off += w
                }
            }
        } catch (_: Throwable) {
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
