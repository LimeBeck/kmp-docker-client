package dev.limebeck.libs.docker.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import dev.limebeck.libs.docker.client.api.*
import dev.limebeck.libs.docker.client.model.AuthConfig
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

}
