package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.api.swarm
import dev.limebeck.libs.docker.client.diagnostics.dockerContext
import dev.limebeck.libs.docker.client.model.ConfigSpec
import dev.limebeck.libs.docker.client.model.SecretSpec
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLDecoder
import kotlin.test.*

class SwarmResourcesHttpTest {
    private fun client(daemon: MockDockerDaemon) = DockerClient(DockerClientConfig(
        connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString())))

    @Test fun requestShapesFiltersAndUnsignedVersions() = runBlocking {
        for (resource in listOf("secrets", "configs")) {
            MockDockerDaemon(listOf(DockerReply("[]"), DockerReply("{\"ID\":\"new-id\"}", "201 Created"),
                DockerReply("{\"ID\":\"new-id\",\"Version\":{\"Index\":18446744073709551615}}"),
                DockerReply(), DockerReply(status = "204 No Content"))).use { daemon ->
                client(daemon).use { docker ->
                    val filters = mapOf("label" to listOf("owner=a+b & c"))
                    if (resource == "secrets") {
                        val api = docker.swarm.secrets
                        api.getList(filters).getOrThrow()
                        assertEquals("new-id", api.create(SecretSpec(name="test", data="aGVsbG8=")).getOrThrow().id)
                        assertEquals(ULong.MAX_VALUE, api.getInfo("name ?#").getOrThrow().version?.index)
                        api.update("new-id", ULong.MAX_VALUE, SecretSpec(name="test", labels=mapOf("a" to "b"))).getOrThrow()
                        api.remove("new-id").getOrThrow()
                    } else {
                        val api = docker.swarm.configs
                        api.getList(filters).getOrThrow()
                        assertEquals("new-id", api.create(ConfigSpec(name="test", data="aGVsbG8=")).getOrThrow().id)
                        assertEquals(ULong.MAX_VALUE, api.getInfo("name ?#").getOrThrow().version?.index)
                        api.update("new-id", ULong.MAX_VALUE, ConfigSpec(name="test", labels=mapOf("a" to "b"))).getOrThrow()
                        api.remove("new-id").getOrThrow()
                    }
                    assertEquals(5, daemon.requests.size)
                    val uris = daemon.requests.map { URI(it.line.split(' ')[1]) }
                    assertEquals(Json.encodeToString(filters), URLDecoder.decode(uris[0].rawQuery.substringAfter("filters="), "UTF-8"))
                    assertEquals("/v1.51/$resource/name ?#", uris[2].path)
                    assertNull(uris[2].rawQuery)
                    assertEquals("version=18446744073709551615", uris[3].rawQuery)
                    val body = Json.parseToJsonElement(daemon.requests[1].body).jsonObject
                    assertEquals("aGVsbG8=", body["Data"]?.jsonPrimitive?.content)
                    assertEquals("application/json", daemon.requests[1].headers["content-type"]?.substringBefore(';'))
                    assertEquals(listOf("GET", "POST", "GET", "POST", "DELETE"), daemon.requests.map { it.line.substringBefore(' ') })
                }
                daemon.checkHealthy()
            }
        }
    }

    @Test fun errorsKeepStatusAndRedactResourceNamesFromContext() = runBlocking {
        for (resource in listOf("secrets", "configs")) {
            for (status in listOf("404 Not Found", "409 Conflict", "503 Service Unavailable")) {
                MockDockerDaemon(listOf(DockerReply("{\"message\":\"raw-detail\"}", status))).use { daemon ->
                    client(daemon).use { docker ->
                        val result = if (resource == "secrets") docker.swarm.secrets.getInfo("private-name")
                                     else docker.swarm.configs.getInfo("private-name")
                        assertEquals("raw-detail", result.errorOrNull()?.message)
                        val failure = assertFails { result.getOrThrow() }
                        val context = assertNotNull(failure.dockerContext)
                        assertEquals("/$resource/{resource}", context.route)
                        assertEquals(status.take(3).toInt(), context.httpStatus)
                        assertFalse(context.toString().contains("private-name"))
                    }
                    daemon.checkHealthy()
                }
            }
        }
    }

    @Test fun cancellationClosesSwarmRequest() = runBlocking {
        MockDockerDaemon(listOf(DockerReply(sendResponse = false))).use { daemon ->
            client(daemon).use { docker ->
                val job = launch { docker.swarm.secrets.getList() }
                withTimeout(5_000) { while (daemon.requests.isEmpty()) delay(10) }
                job.cancelAndJoin()
                withTimeout(5_000) { while (!daemon.peerClosed.get()) delay(10) }
            }
            daemon.checkHealthy()
        }
    }
}
