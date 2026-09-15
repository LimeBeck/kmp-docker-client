package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.api.*
import dev.limebeck.libs.docker.client.diagnostics.*
import dev.limebeck.libs.docker.client.model.DockerApiException
import dev.limebeck.libs.docker.client.model.ExecSession
import dev.limebeck.libs.docker.client.model.LogLine
import dev.limebeck.libs.docker.client.socket.DockerRawConnection
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import kotlin.test.*

class OperationContextTest {
    private suspend fun withDaemon(reply: DockerReply, block: suspend (DockerClient) -> Unit) {
        MockDockerDaemon(listOf(reply)).use { daemon ->
            DockerClient(DockerClientConfig(connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString()))).use {
                withTimeout(5_000) { block(it) }
            }
            daemon.checkHealthy()
        }
    }

    @Test fun requestFailureKeepsTypeAndAddsSafeRoute() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("operation-context-")
        try {
            DockerClient(DockerClientConfig(connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(dir.resolve("secret.sock").toString()))).use { client ->
                val failure = assertFailsWith<java.net.SocketException> { client.containers.getInfo("private-container") }
                val context = assertNotNull(failure.dockerContext)
                assertEquals("GET", context.method)
                assertEquals("/containers/{resource}/json", context.route)
                assertEquals(DockerClient.API_VERSION, context.apiVersion)
                assertEquals(DockerFailureStage.REQUEST, context.stage)
                assertNull(context.httpStatus)
                assertFalse(context.toString().contains("secret"))
                assertFalse(context.toString().contains("private-container"))
            }
        } finally { Files.delete(dir) }
    }

    @Test fun responseDecodingHasStatusAndOriginalExceptionType() = runBlocking<Unit> {
        withDaemon(DockerReply("not JSON secret-body")) { client ->
            val failure = assertFailsWith<SerializationException> { client.containers.getInfo("secret-id") }
            val context = assertNotNull(failure.dockerContext)
            assertEquals(DockerFailureStage.RESPONSE, context.stage)
            assertEquals(200, context.httpStatus)
            assertFalse(context.toString().contains("secret"))
        }
    }

    @Test fun streamHttpErrorRetainsRawDetailsOutsideSafeMessage() = runBlocking<Unit> {
        withDaemon(DockerReply("{\"message\":\"secret-daemon-detail\"}", "404 Not Found")) { client ->
            val failure = assertFailsWith<DockerApiException> { client.containers.getStats("secret-id").getOrThrow().first() }
            assertEquals("secret-daemon-detail", failure.error.message)
            assertFalse(failure.toString().contains("secret"))
            val context = assertNotNull(failure.dockerContext)
            assertEquals("/containers/{resource}/stats", context.route)
            assertEquals(DockerFailureStage.STREAM, context.stage)
            assertEquals(404, context.httpStatus)
        }
    }

    @Test fun malformedStreamCarriesContext() = runBlocking<Unit> {
        withDaemon(DockerReply("not JSON\n")) { client ->
            val failure = assertFailsWith<SerializationException> { client.containers.getStats("container").getOrThrow().first() }
            assertEquals(DockerFailureStage.STREAM, failure.dockerContext?.stage)
            assertEquals(200, failure.dockerContext?.httpStatus)
        }
    }

    @Test fun handshakeFailureIsNotReducedToAnErrorString() = runBlocking<Unit> {
        withDaemon(DockerReply(status = "malformed-secret-status")) { client ->
            val failure = assertFailsWith<IllegalStateException> { client.exec.startInteractive("secret-exec") }
            val context = assertNotNull(failure.dockerContext)
            assertEquals(DockerFailureStage.HANDSHAKE, context.stage)
            assertEquals("/exec/{resource}/start", context.route)
            assertFalse(context.toString().contains("secret"))
        }
    }

    @Test fun cancelledRequestsRemainUnannotated() = runBlocking<Unit> {
        withDaemon(DockerReply(sendResponse = false)) { client ->
            val failure = assertFailsWith<TimeoutCancellationException> {
                withTimeout(200) { client.system.ping() }
            }
            assertNull(failure.dockerContext)
        }
    }
    @Test fun sessionReadAndWriteKeepTheirOriginalCause() = runBlocking<Unit> {
        for (readFailure in listOf(true, false)) {
            val expected = java.io.IOException("private transport details")
            val channel = ByteChannel(autoFlush = true)
            if (!readFailure) channel.close(expected)
            var closed = false
            val connection = object : DockerRawConnection {
                override val read: ByteReadChannel = channel
                override val write: ByteWriteChannel = channel
                override fun close() { closed = true; channel.cancel() }
            }
            ExecSession(flow<LogLine> { throw expected }, true, connection).use { session ->
                session.operationContext = operationContext("POST", "/exec/private-id/start", DockerFailureStage.HANDSHAKE, 101)
                val failure = assertFailsWith<java.io.IOException> {
                    if (readFailure) session.incoming.first() else session.send("input-secret")
                }
                val context = assertNotNull(failure.dockerContext)
                assertEquals(if (readFailure) DockerFailureStage.SESSION_READ else DockerFailureStage.SESSION_WRITE, context.stage)
                assertEquals(101, context.httpStatus)
                assertFalse(context.toString().contains("private"))
                assertTrue(failure === expected || failure.cause === expected)
            }
            assertTrue(closed)
        }
    }

    @Test fun resultErrorsRetainContextThroughGetOrThrowAndMapping() = runBlocking<Unit> {
        for (logs in listOf(false, true)) {
            withDaemon(DockerReply("{\"message\":\"private daemon details\"}", "404 Not Found")) { client ->
                val result = if (logs) client.containers.getLogs("private-id") else client.containers.getInfo("private-id")
                val mapped = result.map { Unit }.mapError { it }
                val failure = assertFailsWith<DockerResultException> { mapped.getOrThrow() }
                assertNotNull(failure.error)
                assertEquals(404, failure.dockerContext?.httpStatus)
                assertEquals("/containers/{resource}/json", failure.dockerContext?.route)
                assertFalse(failure.toString().contains("private"))
            }
        }
    }

    @Test fun malformedImageProgressRetainsParsingCause() = runBlocking<Unit> {
        withDaemon(DockerReply("{not JSON}\n")) { client ->
            val result = client.images.create("alpine")
            assertTrue(result.isError)
            val failure = assertFailsWith<DockerResultException> { result.getOrThrow() }
            assertIs<SerializationException>(failure.cause)
            assertEquals("/images/create", failure.dockerContext?.route)
            assertEquals(DockerFailureStage.STREAM, failure.dockerContext?.stage)
        }
    }

}
