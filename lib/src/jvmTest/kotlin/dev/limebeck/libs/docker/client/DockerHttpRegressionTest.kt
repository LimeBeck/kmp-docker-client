package dev.limebeck.libs.docker.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import dev.limebeck.libs.docker.client.api.*
import dev.limebeck.libs.docker.client.model.ImageProgress
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
        timeoutMillis: Long = 5_000,
        block: suspend (DockerClient, MockDockerDaemon) -> Unit,
    ) {
        MockDockerDaemon(replies.toList()).use { daemon ->
            val client = DockerClient(DockerClientConfig(
                connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString())
            ))
            try {
                withTimeout(timeoutMillis) { block(client, daemon) }
                daemon.checkHealthy()
            } finally {
                client.client.close()
            }
        }
    }

    private suspend fun stream(client: DockerClient, kind: String): Flow<*> = when (kind) {
        "logs" -> client.containers.getLogs("container").getOrThrow()
        "stats" -> client.containers.getStats("container").getOrThrow()
        else -> client.system.events()
    }

    private fun streamReplies(kind: String, vararg replies: DockerReply): Array<DockerReply> =
        (if (kind == "logs") listOf(DockerReply("{\"Config\":{\"Tty\":true}}")) else emptyList())
            .plus(replies).toTypedArray()

    @Test fun cancellingIdleStreamsReleasesTheirConnections() = runBlocking {
        for (kind in listOf("logs", "stats", "events")) {
            val replies = streamReplies(kind, DockerReply(keepOpen = true))
            withDaemon(*replies) { client, daemon ->
                val source = stream(client, kind)
                val collector = launch { source.collect() }
                while (daemon.requests.size < replies.size || !daemon.responseSent.get()) delay(10)
                collector.cancelAndJoin()
                while (!daemon.peerClosed.get()) delay(10)
            }
        }
    }

    @Test fun streamConsumerFailuresReleaseConnectionsWithoutRetry() = runBlocking {
        for (kind in listOf("logs", "stats", "events")) {
            withDaemon(*streamReplies(kind, DockerReply("{}\n", keepOpen = true))) { client, daemon ->
                val expected = IllegalArgumentException("consumer")
                val actual = assertFailsWith<IllegalArgumentException> {
                    stream(client, kind).collect { throw expected }
                }
                assertEquals(expected.message, actual.message)
                while (!daemon.peerClosed.get()) delay(10)
            }
        }
    }

    @Test fun streamsCanBeRecollectedAfterBrokenHttpBody() = runBlocking {
        for (kind in listOf("logs", "stats", "events")) {
            withDaemon(*streamReplies(kind,
                DockerReply("{}\n", declaredLength = 100), DockerReply("{}\n")
            )) { client, daemon ->
                val source = stream(client, kind)
                assertFails { source.toList() }
                assertEquals(1, source.toList().size)
                assertEquals(if (kind == "logs") 3 else 2, daemon.requests.size)
            }
        }
    }

    @Test fun truncatedChunkedStreamsFailAndCanBeRecollected() = runBlocking {
        for (kind in listOf("logs", "stats", "events")) {
            withDaemon(*streamReplies(kind,
                DockerReply("5\r\n{}\n", headers = mapOf("Transfer-Encoding" to "chunked")),
                DockerReply("{}\n")
            )) { client, _ ->
                val source = stream(client, kind)
                assertFails { source.toList() }
                assertEquals(1, source.toList().size)
            }
        }
    }

    @Test fun disconnectBetweenCompleteChunksSupportsEofRecovery() = runBlocking {
        for (kind in listOf("logs", "stats", "events")) {
            withDaemon(*streamReplies(kind,
                DockerReply("3\r\n{}\n\r\n", headers = mapOf("Transfer-Encoding" to "chunked")),
                DockerReply("{}\n")
            )) { client, _ ->
                val source = stream(client, kind)
                // CIO reports EOF at a complete chunk boundary without requiring the terminal zero chunk.
                assertEquals(1, source.toList().size)
                assertEquals(1, source.toList().size)
            }
        }
    }

    @Test fun cleanEofSupportsExplicitResubscription() = runBlocking {
        for (kind in listOf("logs", "stats", "events")) {
            withDaemon(*streamReplies(kind, DockerReply("{}"), DockerReply("{}"))) { client, _ ->
                val source = stream(client, kind)
                repeat(2) { assertEquals(1, source.toList().size) }
            }
        }
    }

    @Test fun oversizedStreamRecordsFailAndReleaseTheResponse() = runBlocking {
        for (kind in listOf("logs", "stats", "events")) {
            withDaemon(*streamReplies(kind, DockerReply("x".repeat(1024 * 1024 + 1) + "\n", keepOpen = true))) { client, daemon ->
                assertFails { stream(client, kind).collect { fail("Oversized record emitted") } }
                while (!daemon.peerClosed.get()) delay(10)
            }
        }
    }

    @Test fun oversizedImageProgressReturnsAnError() = runBlocking {
        withDaemon(DockerReply("x".repeat(1024 * 1024 + 1) + "\n")) { client, _ ->
            assertNotNull(client.images.create("alpine").errorOrNull())
        }
    }

    @Test fun malformedStatsFailInsteadOfEmittingEmptyStatistics() = runBlocking {
        withDaemon(DockerReply("{broken}\n", keepOpen = true)) { client, daemon ->
            assertFailsWith<kotlinx.serialization.SerializationException> {
                client.containers.getStats("container").getOrThrow().collect { fail("Unexpected stats") }
            }
            while (!daemon.peerClosed.get()) delay(10)
        }
    }

    @Test fun eventsKeepCursorAndFiltersWhenResubscribing() = runBlocking {
        withDaemon(DockerReply("{}\n"), DockerReply("{}\n")) { client, daemon ->
            val source = client.system.events(since = "123.456", until = "789", filters = mapOf("type" to listOf("container")))
            repeat(2) { source.toList() }
            for (request in daemon.requests) {
                val url = Url("http://localhost" + request.line.split(' ')[1])
                assertEquals("123.456", url.parameters["since"])
                assertEquals("789", url.parameters["until"])
                assertEquals("{\"type\":[\"container\"]}", url.parameters["filters"])
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

    @Test fun ttyPromptArrivesWhileSocketRemainsOpenAndFirstReleasesSocket() = runBlocking {
        withDaemon(DockerReply("prompt> ", "101 Switching Protocols", keepOpen = true)) { client, daemon ->
            val session = client.exec.startInteractive("probe").getOrThrow()
            assertEquals("prompt> ", session.incomingChunks.first().bytes.decodeToString())
            while (!daemon.peerClosed.get()) delay(10)
        }
    }

    @Test fun cancellingIdleTerminalReleasesSocket() = runBlocking {
        withDaemon(DockerReply("", "101 Switching Protocols", keepOpen = true)) { client, daemon ->
            val session = client.exec.startInteractive("probe").getOrThrow()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { session.incomingChunks.collect() }
            collector.cancelAndJoin()
            while (!daemon.peerClosed.get()) delay(10)
        }
    }

    @Test fun terminalCanSendWhileWaitingForMoreOutput() = runBlocking {
        withDaemon(DockerReply("prompt> ", "101 Switching Protocols", keepOpen = true, expectedInput = "hello")) { client, daemon ->
            val session = client.exec.startInteractive("probe").getOrThrow()
            val prompt = CompletableDeferred<Unit>()
            val reader = async {
                val output = StringBuilder()
                session.incomingChunks.first {
                    output.append(it.bytes.decodeToString())
                    if (output.contains("prompt> ")) prompt.complete(Unit)
                    output.contains("accepted")
                }
            }
            prompt.await()
            session.send("hello")
            reader.await()
            while (!daemon.peerClosed.get()) delay(10)
        }
    }

    @Test fun repeatedTerminalSessionsReleaseConnectionsIncludingUncollectedOutput() = runBlocking {
        val replies = Array(20) { DockerReply("prompt> ", "101 Switching Protocols", keepOpen = true) }
        withDaemon(*replies, timeoutMillis = 30_000) { client, daemon ->
            repeat(replies.size) { index ->
                withTimeout(5_000) {
                    client.exec.startInteractive("probe-$index").getOrThrow().use { session ->
                        if (index % 2 == 0) session.incomingChunks.first()
                    }
                }
            }
            while (!daemon.completed.get()) {
                daemon.checkHealthy()
                delay(10)
            }
            assertTrue(daemon.peerClosed.get(), "The final uncollected session must close its socket")
            assertEquals(replies.size, daemon.requests.size)
        }
    }

    @Test fun cancellingHandshakePropagatesAndReleasesSocket() = runBlocking {
        withDaemon(DockerReply(sendResponse = false)) { client, daemon ->
            var returned = false
            val handshake = launch {
                client.exec.startInteractive("probe")
                returned = true
            }
            while (daemon.requests.isEmpty()) delay(10)
            handshake.cancelAndJoin()
            assertFalse(returned)
            while (!daemon.peerClosed.get()) delay(10)
        }
    }

    @Test fun execCanReadMultiplexedOutputWithoutTty() = runBlocking {
        withDaemon(DockerReply("\u0002\u0000\u0000\u0000\u0000\u0000\u0000\u0003err", "101 Switching Protocols")) { client, daemon ->
            val chunk = client.exec.startInteractive("probe", tty = false).getOrThrow().incomingChunks.first()
            assertEquals(dev.limebeck.libs.docker.client.model.LogLine.Type.STDERR, chunk.type)
            assertEquals("err", chunk.bytes.decodeToString())
            assertTrue(daemon.requests.single().body.contains("\"Tty\":false"))
        }
    }

    private suspend fun imageOperation(
        client: DockerClient,
        kind: String,
        onProgress: suspend (ImageProgress) -> Unit,
    ) = when (kind) {
        "pull" -> client.images.create("alpine", onProgress = onProgress)
        "push" -> client.images.push("alpine", onProgress = onProgress)
        else -> client.images.load(body = ByteReadChannel("archive".encodeToByteArray()), onProgress = onProgress)
    }

    @Test fun imageCallbacksDeliverOrderedProgressBeforeFinalSuccess() = runBlocking {
        for (kind in listOf("pull", "push", "load")) {
            withDaemon(DockerReply("""{"id":"layer","status":"Working","progressDetail":{"current":9007199254740993,"total":18446744073709551615}}
{"stream":"Loaded image","aux":{"ID":"sha256:result"},"extension":true}""")) { client, daemon ->
                val records = mutableListOf<ImageProgress>()
                val result = imageOperation(client, kind) { records += it }
                assertTrue(result.isSuccess)
                assertEquals(2, records.size)
                assertEquals("layer", records[0].id)
                assertEquals("Working", records[0].status)
                assertEquals(9007199254740993uL, records[0].current)
                assertEquals(ULong.MAX_VALUE, records[0].total)
                assertEquals("Loaded image", records[1].stream)
                assertNotNull(records[1].aux)
                assertNotNull(records[1].raw["extension"])
                if (kind == "load") assertEquals("archive", daemon.requests.single().body)
            }
        }
    }

    @Test fun imageCallbacksArePromptAndBackpressuredAndCancellationClosesResponse() = runBlocking {
        for (kind in listOf("pull", "push", "load")) {
            withDaemon(DockerReply("{\"status\":\"one\"}\n{\"status\":\"two\"}\n", keepOpen = true)) { client, daemon ->
                val entered = CompletableDeferred<Unit>()
                var calls = 0
                val operation = launch {
                    imageOperation(client, kind) {
                        calls++
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                }
                entered.await() // The peer never finishes: the callback must arrive before EOF.
                assertEquals(1, calls)
                assertTrue(operation.isActive)
                operation.cancelAndJoin()
                while (!daemon.peerClosed.get()) delay(10)
                assertEquals(1, calls)
            }
        }
    }

    @Test fun imageConsumerFailurePropagatesUnchangedAndClosesResponse() = runBlocking {
        for (kind in listOf("pull", "push", "load")) {
            withDaemon(DockerReply("{}\n", keepOpen = true)) { client, daemon ->
                val expected = IllegalStateException("consumer failure")
                val actual = assertFailsWith<IllegalStateException> {
                    imageOperation(client, kind) { throw expected }
                }
                assertSame(expected, actual)
                while (!daemon.peerClosed.get()) delay(10)
            }
        }
    }

    @Test fun imageCallbacksDoNotTurnFailuresIntoSuccess() = runBlocking {
        for (kind in listOf("pull", "push", "load")) {
            for (tail in listOf("{\"errorDetail\":{\"message\":\"denied\"}}", "{\"error\":\"denied\"}", "broken")) {
                withDaemon(DockerReply("{\"status\":\"Working\"}\n$tail\n")) { client, _ ->
                    val records = mutableListOf<ImageProgress>()
                    assertTrue(imageOperation(client, kind) { records += it }.isError)
                    assertEquals(listOf("Working"), records.map { it.status })
                }
            }
            withDaemon(DockerReply("{}\n", declaredLength = 100)) { client, _ ->
                assertTrue(imageOperation(client, kind) {}.isError)
            }
            withDaemon(DockerReply("{\"message\":\"denied\"}", "403 Forbidden")) { client, _ ->
                val result = imageOperation(client, kind) { fail("HTTP errors must not emit progress") }
                assertEquals("denied", result.errorOrNull()?.message)
            }
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
            assertTrue(actual === expected || actual.cause === expected)
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
