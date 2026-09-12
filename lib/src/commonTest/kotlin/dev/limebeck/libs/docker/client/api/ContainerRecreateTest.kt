package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random
import kotlin.test.*

/** spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#containers.recreate */
class ContainerRecreateTest {
    @Test
    fun recreateRetainsDataAndAppliesChangedConfiguration() = runTest {
        val client = DockerClient()
        val prefix = "recreate-${Random.nextLong().toULong().toString(16)}"
        val volumeName = "$prefix-data"
        val networkName = "$prefix-net"
        val ids = mutableListOf<String>()
        var volumeCreated = false
        var networkId: String? = null
        try {
            client.images.create("alpine:latest").getOrThrow()
            client.volumes.create(VolumeCreateOptions(name = volumeName)).getOrThrow()
            volumeCreated = true
            networkId = client.networks.create(NetworkCreateRequest(name = networkName)).getOrThrow().id

            val config = ContainerCreateRequest(
                image = "alpine:latest",
                env = listOf("REVISION=first"),
                cmd = listOf("sh", "-c", "echo \"\$REVISION\" >> /data/history; cat /data/history"),
                exposedPorts = mapOf("8080/tcp" to JsonObject(emptyMap())),
                hostConfig = HostConfig(
                    mounts = listOf(Mount(type = Mount.Type.VOLUME, source = volumeName, target = "/data")),
                    portBindings = mapOf("8080/tcp" to listOf(PortBinding(hostIp = "127.0.0.1", hostPort = "0"))),
                ),
                networkingConfig = NetworkingConfig(
                    endpointsConfig = mapOf(networkName to EndpointSettings(aliases = listOf("service"))),
                ),
            )
            suspend fun create(request: ContainerCreateRequest): String =
                client.containers.create(name = prefix, config = request).getOrThrow().id.also { ids += it }

            suspend fun runAndRead(id: String): List<String> {
                client.containers.start(id).getOrThrow()
                assertEquals(0, client.containers.wait(id, "not-running").getOrThrow().statusCode)
                return client.containers.getLogs(id).getOrThrow().toList().map { it.line.trim() }
            }

            val original = create(config)
            val info = client.containers.getInfo(original).getOrThrow()
            assertTrue(info.config?.env.orEmpty().contains("REVISION=first"))
            assertEquals(volumeName, info.mounts?.single { it.destination == "/data" }?.name)
            assertTrue(info.networkSettings?.networks.orEmpty().containsKey(networkName))
            assertEquals("127.0.0.1", info.hostConfig?.portBindings?.get("8080/tcp")?.single()?.hostIp)
            assertEquals(listOf("first"), runAndRead(original))

            // A failed replacement must leave both the old resource and its data available.
            assertTrue(client.containers.create(name = prefix, config = config).isError)
            assertEquals(original, client.containers.getInfo(original).getOrThrow().id)
            client.containers.remove(original).getOrThrow()
            assertEquals(volumeName, client.volumes.getInfo(volumeName).getOrThrow().name)
            assertTrue(client.containers.create(name = prefix, config = config.copy(image = "$prefix:missing")).isError)
            assertEquals(volumeName, client.volumes.getInfo(volumeName).getOrThrow().name)

            val replacement = create(config.copy(env = listOf("REVISION=second")))
            assertNotEquals(original, replacement)
            assertTrue(client.containers.getInfo(replacement).getOrThrow().config?.env.orEmpty().contains("REVISION=second"))
            client.containers.update(replacement, ContainerUpdateRequest(
                restartPolicy = RestartPolicy(name = RestartPolicy.Name.ON_FAILURE, maximumRetryCount = 3),
            )).getOrThrow()
            assertEquals(3, client.containers.getInfo(replacement).getOrThrow().hostConfig?.restartPolicy?.maximumRetryCount)
            assertEquals(listOf("first", "second"), runAndRead(replacement))
            client.containers.remove(replacement).getOrThrow()

            // Reading from a third container proves data survives deleting the replacement too.
            val reader = create(config.copy(cmd = listOf("cat", "/data/history")))
            assertEquals(listOf("first", "second"), runAndRead(reader))
            client.containers.remove(reader).getOrThrow()
            client.volumes.remove(volumeName).getOrThrow()
            volumeCreated = false
            assertTrue(client.volumes.getInfo(volumeName).isError)
        } finally {
            withContext(NonCancellable) {
                try {
                    ids.forEach { client.containers.remove(it, force = true) }
                    networkId?.let { client.networks.remove(it).getOrThrow() }
                    if (volumeCreated) client.volumes.remove(volumeName).getOrThrow()
                } finally {
                    client.client.close()
                }
            }
        }
    }
}
