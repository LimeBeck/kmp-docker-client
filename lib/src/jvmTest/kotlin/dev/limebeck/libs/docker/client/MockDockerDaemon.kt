package dev.limebeck.libs.docker.client

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal data class DockerReply(
    val body: String = "",
    val status: String = "200 OK",
    val headers: Map<String, String> = emptyMap(),
    val keepOpen: Boolean = false,
    val expectedInput: String? = null,
    val sendResponse: Boolean = true,
)

internal data class DockerRequest(val line: String, val headers: Map<String, String>, val body: String)

/** A real Unix HTTP peer, so tests cover CIO request construction and response lifetimes. */
internal class MockDockerDaemon(replies: List<DockerReply>) : AutoCloseable {
    private val directory = Files.createTempDirectory("docker-client-test-")
    val path = directory.resolve("docker.sock")
    val requests = CopyOnWriteArrayList<DockerRequest>()
    val responseSent = AtomicBoolean()
    val peerClosed = AtomicBoolean()
    private val stopped = AtomicBoolean()
    private val failure = AtomicReference<Throwable?>()
    private val activeSocket = AtomicReference<SocketChannel?>()
    private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply {
        bind(UnixDomainSocketAddress.of(path))
    }
    private val worker = thread(isDaemon = true, name = "mock-docker") {
        try {
            for (reply in replies) {
                server.accept().use { socket ->
                    activeSocket.set(socket)
                    val input = Channels.newInputStream(socket)
                    fun readLine(): String {
                        val line = StringBuilder()
                        while (!line.endsWith("\r\n")) {
                            val byte = input.read()
                            check(byte >= 0) { "Unexpected EOF reading request" }
                            line.append(byte.toChar())
                        }
                        return line.dropLast(2).toString()
                    }
                    val requestLine = readLine()
                    val headers = buildMap {
                        while (true) {
                            val line = readLine()
                            if (line.isEmpty()) break
                            put(line.substringBefore(':').lowercase(), line.substringAfter(':').trim())
                        }
                    }
                    val body = if (headers["transfer-encoding"] == "chunked") {
                        val bytes = java.io.ByteArrayOutputStream()
                        while (true) {
                            val size = readLine().substringBefore(';').toInt(16)
                            if (size == 0) {
                                while (readLine().isNotEmpty()) { /* trailers */ }
                                break
                            }
                            bytes.write(input.readNBytes(size))
                            check(readLine().isEmpty())
                        }
                        bytes.toByteArray()
                    } else {
                        input.readNBytes(headers["content-length"]?.toInt() ?: 0)
                    }
                    requests.add(DockerRequest(requestLine, headers, body.decodeToString()))
                    if (!reply.sendResponse) {
                        peerClosed.set(input.read() == -1)
                        return@use
                    }
                    val payload = reply.body.encodeToByteArray()
                    val output = Channels.newOutputStream(socket)
                    val response = buildString {
                        append("HTTP/1.1 ${reply.status}\r\n")
                        append("Content-Type: application/json\r\nConnection: close\r\n")
                        if (!reply.keepOpen) append("Content-Length: ${payload.size}\r\n")
                        reply.headers.forEach { (key, value) -> append("$key: $value\r\n") }
                        append("\r\n")
                    }
                    output.write(response.encodeToByteArray())
                    output.write(payload)
                    output.flush()
                    responseSent.set(true)
                    reply.expectedInput?.let { expected ->
                        check(input.readNBytes(expected.encodeToByteArray().size).decodeToString() == expected)
                        output.write("accepted".encodeToByteArray())
                        output.flush()
                    }
                    if (reply.keepOpen) peerClosed.set(input.read() == -1)
                }
                activeSocket.set(null)
            }
        } catch (error: Throwable) {
            if (!stopped.get()) failure.set(error)
        }
    }

    fun checkHealthy() {
        failure.get()?.let { throw AssertionError("Mock daemon failed", it) }
    }

    override fun close() {
        stopped.set(true)
        activeSocket.get()?.close()
        server.close()
        worker.join(1000)
        Files.deleteIfExists(path)
        Files.deleteIfExists(directory)
    }
}
