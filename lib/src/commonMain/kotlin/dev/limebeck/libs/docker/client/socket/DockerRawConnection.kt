package dev.limebeck.libs.docker.client.socket

import io.ktor.utils.io.*

interface DockerRawConnection : AutoCloseable {
    val read: ByteReadChannel
    val write: ByteWriteChannel
}

/** Открывает duplex-соединение к Docker daemon по unix socket. */
expect suspend fun openRawConnectionUnix(path: String = "/var/run/docker.sock"): DockerRawConnection
