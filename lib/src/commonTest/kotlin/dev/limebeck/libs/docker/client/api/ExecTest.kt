package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.ContainerConfig
import dev.limebeck.libs.docker.client.model.ExecConfig
import dev.limebeck.libs.docker.client.model.ExecStartConfig
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ExecTest {
    private val client = DockerClient()

    @Test
    fun `Exec should start and return logs`() = runTest {
        val imageName = "alpine:latest"
        client.images.create(fromImage = imageName).getOrThrow()

        val containerName = "test-exec-${kotlin.random.Random.nextInt(1000)}"
        val createResponse = client.containers.create(
            name = containerName,
            config = ContainerConfig(
                image = imageName,
                cmd = listOf("sleep", "10000")
            )
        ).getOrThrow()
        val containerId = createResponse.id
        println("Container ID: $containerId")

        try {
            client.containers.start(containerId).getOrThrow()

            val execId = client.containers.execCreate(
                containerId,
                ExecConfig(
                    cmd = listOf("echo", "hello"),
                    attachStdout = true,
                    attachStderr = true,
                    tty = true
                )
            ).getOrThrow().id

            println("Exec ID: $execId")

            val result = client.exec.startInteractive(execId, ExecStartConfig(detach = false, tty = true)).getOrThrow()

            val exec = client.exec.getInfo(execId).getOrNull()
            println(exec)

            assertTrue(result.incoming.first().line.contains("hello"))
        } finally {
            client.containers.remove(containerId, force = true, v = true).getOrThrow()
        }
    }

    @Test
    fun `Exec should start and send user input`() = runTest {
        val imageName = "alpine:latest"
        client.images.create(fromImage = imageName).getOrThrow()

        val containerName = "test-exec-${kotlin.random.Random.nextInt(1000)}"
        val createResponse = client.containers.create(
            name = containerName,
            config = ContainerConfig(
                image = imageName,
                cmd = listOf("sleep", "10000")
            )
        ).getOrThrow()
        val containerId = createResponse.id
        println("Container ID: $containerId")

        try {
            client.containers.start(containerId).getOrThrow()

            val execId = client.containers.execCreate(
                containerId,
                ExecConfig(
                    cmd = listOf("ash"),
                    attachStdout = true,
                    attachStderr = true,
                    attachStdin = true,
                    tty = true
                )
            ).getOrThrow().id

            println("Exec ID: $execId")

            val result = client.exec.startInteractive(execId, ExecStartConfig(detach = false, tty = true)).getOrThrow()

            val exec = client.exec.getInfo(execId).getOrNull()
            println(exec)

            result.send("echo \"hello\"\n")
            result.send("echo \"hello\"\n")

            result.incoming.filter { it.line == "hello" }.first()

            result.close()
        } finally {
            client.containers.remove(containerId, force = true).getOrThrow()
        }
    }
}
