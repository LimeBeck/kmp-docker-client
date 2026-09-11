package dev.limebeck.libs.docker.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import dev.limebeck.libs.docker.client.api.*
import dev.limebeck.libs.docker.client.model.AuthConfig
import dev.limebeck.libs.docker.client.model.DockerApiException
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.encoding.Base64
import kotlin.test.*

/**
 * Regression coverage:
 * spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-000.md#auth.logging
 * spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.streams
 * spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#images.progress
 */
class DockerHttpRegressionTest {
    private suspend fun withDaemon(
        vararg replies: DockerReply,
        block: suspend (DockerClient, MockDockerDaemon) -> Unit,
    ) {
        MockDockerDaemon(replies.toList()).use { daemon ->
            val client = DockerClient(DockerClientConfig(
                connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString())
            ))
            try {
                withTimeout(5000) { block(client, daemon) }
                daemon.checkHealthy()
            } finally {
                client.client.close()
            }
        }
    }

    @Test fun ordinaryRequestsUseVersionedPathsAndPreserveQuery() = runBlocking {
        withDaemon(DockerReply("[]"), DockerReply("OK")) { client, daemon ->
            client.containers.getList(all = true, filters = mapOf("name" to listOf("a b"))).getOrThrow()
            client.system.ping().getOrThrow()
            val url = Url("http://localhost" + daemon.requests[0].line.split(' ')[1])
            assertEquals("/v1.51/containers/json", url.encodedPath)
            assertEquals("true", url.parameters["all"])
            assertEquals("{\"name\":[\"a b\"]}", url.parameters["filters"])
            assertEquals("GET /v1.51/_ping HTTP/1.1", daemon.requests[1].line)
        }
    }

    @Test fun hijackUsesTheSameVersionPrefix() = runBlocking {
        withDaemon(DockerReply("hello\n", "101 Switching Protocols")) { client, daemon ->
            client.exec.startInteractive("probe").getOrThrow().use { session ->
                assertEquals(listOf("hello"), session.incoming.map { it.line }.toList())
            }
            assertEquals("POST /v1.51/exec/probe/start HTTP/1.1", daemon.requests.single().line)
        }
    }

    @Test fun pullReportsErrorAfterProgress() = runBlocking {
        withDaemon(DockerReply("{\"status\":\"Pulling\"}\n{\"errorDetail\":{\"message\":\"pull failed\"}}\n")) { client, _ ->
            assertEquals("pull failed", client.images.create("alpine:latest").errorOrNull()?.message)
        }
    }

    @Test fun pushReportsLegacyError() = runBlocking {
        withDaemon(DockerReply("{\"status\":\"Pushing\"}\n{\"error\":\"push failed\"}\n")) { client, daemon ->
            assertEquals("push failed", client.images.push("alpine", tag = "latest").errorOrNull()?.message)
            assertTrue(daemon.requests.single().line.startsWith("POST /v1.51/images/alpine/push?"))
        }
    }

    @Test fun loadReportsStreamErrorAndUploadsTheBody() = runBlocking {
        withDaemon(DockerReply("{\"errorDetail\":{\"message\":\"invalid archive\"}}\n")) { client, daemon ->
            assertEquals("invalid archive", client.images.load(body = ByteReadChannel("archive".encodeToByteArray())).errorOrNull()?.message)
            assertEquals("archive", daemon.requests.single().body)
        }
    }

    @Test fun completedProgressReturnsSuccess() = runBlocking {
        withDaemon(DockerReply("{\"status\":\"Pulling\"}\n\n{\"status\":\"Done\"}\n")) { client, _ ->
            assertTrue(client.images.create("alpine:latest").isSuccess)
        }
    }

    @Test fun malformedProgressDoesNotReturnSuccess() = runBlocking {
        withDaemon(DockerReply("{\"status\":\"Pulling\"}\nnot-json\n")) { client, _ ->
            assertTrue(client.images.create("alpine:latest").isError)
        }
    }

    @Test fun imageHttpErrorRetainsDaemonMessage() = runBlocking {
        withDaemon(DockerReply("{\"message\":\"denied\"}", "403 Forbidden")) { client, _ ->
            assertEquals("denied", client.images.create("alpine:latest").errorOrNull()?.message)
        }
    }

    @Test fun cancellingImageProgressClosesTheResponse() = runBlocking {
        withDaemon(DockerReply("{\"status\":\"Pulling\"}\n", keepOpen = true)) { client, daemon ->
            coroutineScope {
                val operation = async { client.images.create("alpine:latest") }
                while (!daemon.responseSent.get()) delay(10)
                assertFalse(operation.isCompleted)
                operation.cancelAndJoin()
                while (!daemon.peerClosed.get()) delay(10)
            }
        }
    }

    @Test fun statsIsColdAndRejectsHttpErrorsBeforeEmitting() = runBlocking {
        withDaemon(DockerReply("{\"message\":\"No such container\"}\n", "404 Not Found")) { client, daemon ->
            val stats = client.containers.getStats("missing").getOrThrow()
            assertTrue(daemon.requests.isEmpty())
            var emitted = false
            val error = assertFailsWith<DockerApiException> { stats.collect { emitted = true } }
            assertFalse(emitted)
            assertEquals(HttpStatusCode.NotFound, error.status)
            assertEquals("No such container", error.error.message)
            assertTrue(daemon.requests.single().line.startsWith("GET /v1.51/containers/missing/stats?"))
        }
    }

    @Test fun oneShotStatsRetainsResultErrorContract() = runBlocking {
        withDaemon(DockerReply("{\"message\":\"missing\"}", "404 Not Found")) { client, _ ->
            assertEquals("missing", client.containers.getStats("missing", stream = false).errorOrNull()?.message)
        }
    }

    @Test fun statsEmitsBeforeEofAndClosesAfterTake() = runBlocking {
        withDaemon(DockerReply("{\"id\":\"container\"}\n", keepOpen = true)) { client, daemon ->
            val sample = client.containers.getStats("container").getOrThrow().take(1).single()
            assertEquals("container", sample.id)
            while (!daemon.peerClosed.get()) delay(10)
        }
    }

    @Test fun logsRejectErrorsAfterSuccessfulInspect() = runBlocking {
        withDaemon(DockerReply("{\"Config\":{\"Tty\":true}}"), DockerReply("{\"message\":\"logs unsupported\"}", "500 Internal Server Error")) { client, _ ->
            val logs = client.containers.getLogs("container").getOrThrow()
            val error = assertFailsWith<DockerApiException> { logs.collect { fail("Must not emit HTTP error body") } }
            assertEquals("logs unsupported", error.error.message)
        }
    }

    @Test fun eventsPropagatesTheOriginalConsumerException() = runBlocking {
        withDaemon(DockerReply("{}\n{}\n")) { client, _ ->
            val expected = IllegalStateException("consumer failed")
            val actual = assertFailsWith<IllegalStateException> { client.system.events().collect { throw expected } }
            assertSame(expected, actual)
        }
    }

    @Test fun eventsSkipsMalformedRecords() = runBlocking {
        withDaemon(DockerReply("{\"Action\":\"start\"}\nnot-json\n{\"Action\":\"stop\"}\n")) { client, daemon ->
            assertEquals(listOf("start", "stop"), client.system.events().map { it.action }.toList())
            assertEquals("GET /v1.51/events HTTP/1.1", daemon.requests.single().line)
        }
    }

    @Test fun takingOneEventClosesAnOpenStream() = runBlocking {
        withDaemon(DockerReply("{}\n", keepOpen = true)) { client, daemon ->
            assertEquals(1, client.system.events().take(1).toList().size)
            while (!daemon.peerClosed.get()) delay(10)
        }
    }

    @Test fun eventsRejectsHttpErrorsWithBodyFallback() = runBlocking {
        withDaemon(DockerReply("not JSON", "502 Bad Gateway")) { client, _ ->
            val error = assertFailsWith<DockerApiException> { client.system.events().collect { fail("Unexpected event") } }
            assertEquals(HttpStatusCode.BadGateway, error.status)
            assertTrue(error.error.message.contains("502"))
        }
    }

    @Test fun headErrorsReturnResultWithoutJsonDecoding() = runBlocking {
        for (status in listOf("404 Not Found", "500 Internal Server Error")) {
            withDaemon(DockerReply(status = status)) { client, _ ->
                val result = client.containers.getArchiveInfo("missing", "/missing")
                assertTrue(result.isError)
                assertTrue(result.errorOrNull()!!.message.contains(status))
            }
        }
    }

    @Test fun headSuccessPreservesPathStatHeader() = runBlocking {
        withDaemon(DockerReply(headers = mapOf("X-Docker-Container-Path-Stat" to "eyJuYW1lIjoiYSJ9"))) { client, _ ->
            assertEquals("eyJuYW1lIjoiYSJ9", client.containers.getArchiveInfo("container", "/a").getOrThrow())
        }
    }

    @Test fun httpLogsNeverExposeAuthOrBodies() = runBlocking {
        val messages = ConcurrentLinkedQueue<String>()
        val logger = LoggerFactory.getLogger(DockerClient::class.java) as Logger
        val oldLevel = logger.level
        val appender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) { messages.add(event.formattedMessage) }
        }.apply { start() }
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        try {
            withDaemon(
                DockerReply("{\"Status\":\"Login Succeeded\",\"IdentityToken\":\"auth-response-secret\"}"),
                DockerReply("{\"status\":\"progress-body-secret\"}\n"),
                DockerReply("{\"status\":\"progress-body-secret\"}\n"),
                DockerReply("response-body-secret"),
            ) { client, daemon ->
                client.auth(AuthConfig(username = "review-user", password = "auth-password-secret")).getOrThrow()
                client.images.create("alpine:latest").getOrThrow()
                client.config.auth.clear()
                client.config.auth["docker.io"] = DockerClientConfig.Auth.Credentials("review-user", "registry-password-secret")
                client.images.create("alpine:latest").getOrThrow()
                client.client.post(client.apiPath("/probe")) {
                    header("aUtHoRiZaTiOn", "Bearer authorization-secret")
                    header("Proxy-Authorization", "proxy-secret")
                    header("x-registry-config", "registry-config-secret")
                    setBody("request-body-secret")
                }
                while (messages.count { it.contains("RESPONSE:") } < 3) delay(10)
                val logs = messages.joinToString("\n")
                val encodedToken = daemon.requests[1].headers.getValue("x-registry-auth")
                assertTrue(Base64.decode(encodedToken).decodeToString().contains("auth-response-secret"))
                assertFalse(logs.contains(encodedToken))
                val encodedAuth = daemon.requests[2].headers.getValue("x-registry-auth")
                assertTrue(Base64.decode(encodedAuth).decodeToString().contains("registry-password-secret"))
                assertFalse(logs.contains(encodedAuth))
                for (secret in listOf("auth-password-secret", "auth-response-secret", "registry-password-secret", "progress-body-secret", "authorization-secret", "proxy-secret", "registry-config-secret", "request-body-secret", "response-body-secret")) {
                    assertFalse(logs.contains(secret), "HTTP logs exposed $secret")
                }
                assertFalse(logs.contains("/v1.51/auth"))
                assertTrue(logs.contains("/v1.51/images/create"), "The test must actually capture HTTP logs")
            }
        } finally {
            logger.detachAppender(appender)
            logger.level = oldLevel
            appender.stop()
        }
    }
}
