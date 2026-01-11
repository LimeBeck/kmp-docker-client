package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.ContainerConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContainersTest {
    private val client = DockerClient()

    @Test
    fun testListContainers() = runTest {
        client.containers.getList().getOrThrow()
    }

    @Test
    fun testContainerLifecycle() = runTest {
        val imageName = "alpine:latest"
        
        // Ensure image exists
        client.images.create(fromImage = imageName).getOrThrow()

        val containerName = "test-container-${kotlin.random.Random.nextInt(1000)}"
        
        // Create
        val createResponse = client.containers.create(
            name = containerName,
            config = ContainerConfig(
                image = imageName,
                cmd = listOf("sleep", "10")
            )
        ).getOrThrow()
        val containerId = createResponse.id

        try {
            // Start
            client.containers.start(containerId).getOrThrow()

            // Get Info
            val info = client.containers.getInfo(containerId).getOrThrow()
            assertEquals(info.state?.running, true)

            // Stop
            client.containers.stop(containerId).getOrThrow()
            
            val infoAfterStop = client.containers.getInfo(containerId).getOrThrow()
            assertEquals(infoAfterStop.state?.running, false)
        } finally {
            // Remove
            client.containers.remove(containerId, force = true).getOrThrow()
        }
    }

    @Test
    fun testContainerLogs() = runTest {
        val imageName = "alpine:latest"
        client.images.create(fromImage = imageName).getOrThrow()

        val containerName = "test-logs-${kotlin.random.Random.nextInt(1000)}"
        val createResponse = client.containers.create(
            name = containerName,
            config = ContainerConfig(
                image = imageName,
                cmd = listOf("echo", "hello world")
            )
        ).getOrThrow()
        val containerId = createResponse.id

        try {
            client.containers.start(containerId).getOrThrow()
            
            val logs = client.containers.getLogs(containerId).getOrThrow().toList()
            assertTrue(logs.any { it.line.contains("hello world") })
        } finally {
            client.containers.remove(containerId, force = true).getOrThrow()
        }
    }

    @Test
    fun testAttach() = runTest {
        val imageName = "alpine:latest"
        client.images.create(fromImage = imageName).getOrThrow()

        val containerName = "test-attach-ws-${kotlin.random.Random.nextInt(1000)}"
        val createResponse = client.containers.create(
            name = containerName,
            config = ContainerConfig(
                image = imageName,
                cmd = listOf("ping", "-c", "1", "localhost"),
                attachStdin = true,
                attachStdout = true,
                attachStderr = true,
                openStdin = true
            )
        ).getOrThrow()
        val containerId = createResponse.id

        try {
            client.containers.start(containerId).getOrThrow()

        } finally {
            client.containers.remove(containerId, force = true).getOrThrow()
        }
    }
}
