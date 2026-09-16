package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.DockerClientConfig
import dev.limebeck.libs.docker.client.model.ConfigSpec
import dev.limebeck.libs.docker.client.model.SecretSpec
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.random.Random

class SwarmResourcesIntegrationTest {
    private fun client() = DockerClient(DockerClientConfig(
        connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(SWARM_SOCKET)))

    @Test fun secretLifecycleAndVersionConflicts() = runTest(timeout = 60.seconds) {
        client().use { docker ->
            assertSame(docker.swarm, docker.swarm)
            assertSame(docker.swarm.secrets, docker.swarm.secrets)
            val api = docker.swarm.secrets
            val name = "kmp-secret-${Random.nextInt(1, Int.MAX_VALUE)}"
            val spec = SecretSpec(name = name, labels = mapOf("test" to name), data = "aGVsbG8=")
            val id = api.create(spec).getOrThrow().id
            try {
                assertTrue(api.create(spec).isError)
                val found = api.getInfo(id).getOrThrow()
                assertEquals(name, found.spec?.name)
                assertNull(found.spec?.data, "Inspect must not expose secret payload")
                val listed = api.getList(mapOf("label" to listOf("test=$name"))).getOrThrow()
                assertEquals(listOf(id), listed.map { it.ID })
                assertNull(listed.single().spec?.data)
                val version = assertNotNull(found.version?.index)
                val updated = assertNotNull(found.spec).copy(labels = mapOf("test" to name, "updated" to "true"))
                api.update(id, version, updated).getOrThrow()
                assertEquals("true", api.getInfo(id).getOrThrow().spec?.labels?.get("updated"))
                assertTrue(api.update(id, version, updated).isError, "Stale version must not overwrite state")
            } finally { api.remove(id).getOrThrow() }
            assertTrue(api.getInfo(id).isError)
            assertTrue(api.remove(id).isError)
        }
    }

    @Test fun configLifecycleKeepsDataAndRejectsStaleVersion() = runTest(timeout = 60.seconds) {
        client().use { docker ->
            assertSame(docker.swarm.configs, docker.swarm.configs)
            val api = docker.swarm.configs
            val name = "kmp-config-${Random.nextInt(1, Int.MAX_VALUE)}"
            val spec = ConfigSpec(name = name, labels = mapOf("test" to name), data = "aGVsbG8=")
            val id = api.create(spec).getOrThrow().id
            try {
                assertTrue(api.create(spec).isError)
                val found = api.getInfo(id).getOrThrow()
                assertEquals(spec.data, found.spec?.data)
                assertEquals(listOf(id), api.getList(mapOf("label" to listOf("test=$name"))).getOrThrow().map { it.ID })
                val version = assertNotNull(found.version?.index)
                val updated = assertNotNull(found.spec).copy(labels = mapOf("test" to name, "updated" to "true"))
                api.update(id, version, updated).getOrThrow()
                val after = api.getInfo(id).getOrThrow()
                assertEquals("true", after.spec?.labels?.get("updated"))
                assertEquals(spec.data, after.spec?.data)
                assertTrue(api.update(id, version, updated).isError)
            } finally { api.remove(id).getOrThrow() }
            assertTrue(api.getInfo(id).isError)
            assertTrue(api.remove(id).isError)
        }
    }
}
