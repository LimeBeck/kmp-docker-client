package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.diagnostics.*
import dev.limebeck.libs.docker.client.api.system
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.*
import java.nio.file.Files
import kotlin.test.*

class ConnectionDiagnosticsTest {
    @Test fun missingSocketHasActionableContext() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("docker-diagnostic-")
        try {
            val path = directory.resolve("absent.sock").toString()
            DockerClient(DockerClientConfig(connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(path))).use {
                val diagnostic = it.diagnoseConnection()
                assertEquals(ConnectionProblem.SOCKET_NOT_FOUND, diagnostic.problem)
                assertFalse(diagnostic.toString().contains(path))
            }
        } finally { Files.delete(directory) }
    }

    @Test fun distinguishesVersionRejectionFromGenericHttpErrors() = runBlocking<Unit> {
        for ((reply, expected) in listOf(
            DockerReply("OK") to ConnectionProblem.NONE,
            DockerReply("not docker") to ConnectionProblem.UNEXPECTED_RESPONSE,
            DockerReply("OK" + " ".repeat(8192), keepOpen = true, allowEarlyClose = true) to ConnectionProblem.UNEXPECTED_RESPONSE,
            DockerReply("{\"message\":\"client version 1.51 is too new\"}", "400 Bad Request") to ConnectionProblem.API_VERSION_UNSUPPORTED,
            DockerReply("bad request", "400 Bad Request") to ConnectionProblem.HTTP_ERROR,
            DockerReply("denied", "403 Forbidden") to ConnectionProblem.PERMISSION_DENIED,
        )) {
            MockDockerDaemon(listOf(reply)).use { daemon ->
                DockerClient(DockerClientConfig(connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString()))).use {
                    assertEquals(expected, withTimeout(5_000) { it.diagnoseConnection(60_000) }.problem)
                    assertEquals("GET /v1.51/_ping HTTP/1.1", daemon.requests.single().line)
                }
                daemon.checkHealthy()
            }
        }
    }

    @Test fun timeoutAndCancellationRemainDistinct() = runBlocking<Unit> {
        MockDockerDaemon(listOf(DockerReply(sendResponse = false))).use { daemon ->
            DockerClient(DockerClientConfig(connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString()))).use {
                assertEquals(ConnectionProblem.TIMEOUT, it.diagnoseConnection(200).problem)
            }
        }
        MockDockerDaemon(listOf(DockerReply(sendResponse = false))).use { daemon ->
            DockerClient(DockerClientConfig(connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString()))).use {
                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(200) { it.diagnoseConnection(5_000) }
                }
            }
        }
    }

    @Test fun classifiesKnownCausesWithoutLeakingExceptionText() {
        DockerClient().use { client ->
            for ((failure, expected) in listOf(
                java.nio.file.AccessDeniedException("secret") to ConnectionProblem.PERMISSION_DENIED,
                java.net.ConnectException("Connection refused") to ConnectionProblem.CONNECTION_REFUSED,
                java.net.SocketException("Connection reset") to ConnectionProblem.CONNECTION_LOST,
                IllegalStateException("connect(/tmp/docker.sock) failed: errno=13") to ConnectionProblem.PERMISSION_DENIED,
                IllegalStateException("application failure: secret") to ConnectionProblem.UNKNOWN,
            )) {
                val wrapper = RuntimeException("wrapper", failure)
                val diagnostic = client.diagnoseFailure(wrapper)
                assertEquals(expected, diagnostic.problem)
                assertFalse(diagnostic.message.contains("secret"))
            }
            val cancelled = CancellationException("stop")
            assertSame(cancelled, assertFailsWith<CancellationException> { client.diagnoseFailure(cancelled) })
        }
    }
    @Test fun reportDoesNotRetainSensitiveInput() {
        val secret = "diagnostic-SENTINEL-secret"
        val failure = RuntimeException("https://user:$secret@registry.example/path?token=$secret",
            java.net.SocketException("Connection reset; Authorization: Bearer $secret"))
        failure.addSuppressed(IllegalStateException("password=$secret"))
        DockerClient(DockerClientConfig(
            connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection("/home/$secret/docker.sock"),
            auth = mutableMapOf("registry.example" to DockerClientConfig.Auth.Token(secret)),
        )).use { client ->
            val report = client.diagnoseFailure(failure)
            assertEquals(ConnectionProblem.CONNECTION_LOST, report.problem)
            assertFalse(report.toString().contains(secret))
            assertFalse(report.message.contains(secret))
            assertFalse(report.suggestion.contains(secret))
            assertTrue(ConnectionDiagnostic::class.java.declaredFields.none {
                Throwable::class.java.isAssignableFrom(it.type) || it.name == "endpoint"
            })
        }
    }

    @Test fun probeDoesNotLogResponseSecretsAtDebug() = runBlocking<Unit> {
        val secret = "diagnostic-HTTP-SENTINEL"
        val messages = ConcurrentLinkedQueue<String>()
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val sdk = LoggerFactory.getLogger("dev.limebeck.libs.docker.client.DockerClient") as Logger
        val oldRoot = root.level
        val oldSdk = sdk.level
        val appender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                messages.add(event.formattedMessage + (event.throwableProxy?.let { ThrowableProxyUtil.asString(it) } ?: ""))
            }
        }.apply { start() }
        root.level = Level.DEBUG
        sdk.level = Level.DEBUG
        root.addAppender(appender)
        try {
            MockDockerDaemon(listOf(
                DockerReply("{\"message\":\"$secret\"}", "500 Internal Server Error",
                    headers = mapOf("X-Diagnostic-Detail" to secret, "Set-Cookie" to "session=$secret")),
                DockerReply("OK"),
            )).use { daemon ->
                DockerClient(DockerClientConfig(connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString()))).use { client ->
                    val report = client.diagnoseConnection()
                    assertEquals(ConnectionProblem.HTTP_ERROR, report.problem)
                    assertFalse(report.toString().contains(secret))
                    client.system.ping().getOrThrow()
                    withTimeout(5_000) { while (messages.none { it.contains("RESPONSE: 200") }) delay(10) }
                }
                daemon.checkHealthy()
            }
            DockerClient(DockerClientConfig(connectionConfig =
                DockerClientConfig.ConnectionConfig.SocketConnection("/tmp/$secret/nonexistent.sock"))).use { client ->
                assertEquals(ConnectionProblem.SOCKET_NOT_FOUND, client.diagnoseConnection().problem)
            }
            assertTrue(messages.any { it.contains("/_ping") }, "Control request must produce HTTP logs")
            assertFalse(messages.joinToString("\n").contains(secret), "Diagnostic response leaked into logs")
        } finally {
            root.detachAppender(appender)
            root.level = oldRoot
            sdk.level = oldSdk
            appender.stop()
        }
    }

}
