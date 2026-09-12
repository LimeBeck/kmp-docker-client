package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.ContainerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LiveStreamsTest {
    @Test fun statsAndEventsCanBeCollectedAcrossEngineDispatchers() = runTest {
        val client = DockerClient()
        try {
            client.images.create("alpine:latest").getOrThrow()
            val id = client.containers.create(config = ContainerConfig(
                image = "alpine:latest", cmd = listOf("sleep", "60")
            )).getOrThrow().id
            try {
                client.containers.start(id).getOrThrow()
                assertEquals(id, client.containers.getStats(id).getOrThrow().first().id)
                assertNotNull(client.system.events(
                    since = "0", filters = mapOf("container" to listOf(id))
                ).first().action)
            } finally {
                client.containers.remove(id, force = true, v = true).getOrThrow()
            }
        } finally { client.client.close() }
    }
}
