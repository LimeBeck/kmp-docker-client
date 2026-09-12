package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.ContainerConfig
import dev.limebeck.libs.docker.client.model.ExecConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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

        val createResponse = client.containers.create(
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

            val result = client.exec.startInteractive(execId).getOrThrow()

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

        val createResponse = client.containers.create(
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

            val result = client.exec.startInteractive(execId).getOrThrow()

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
    @Test
    fun `TTY prompt and reply arrive without newline`() = runTest {
        client.images.create(fromImage = "alpine:latest").getOrThrow()
        val containerId = client.containers.create(config = ContainerConfig(
            image = "alpine:latest", cmd = listOf("sleep", "10000")
        )).getOrThrow().id
        try {
            client.containers.start(containerId).getOrThrow()
            val execId = client.containers.execCreate(containerId, ExecConfig(
                cmd = listOf("sh", "-c", "printf 'ready> '; read value; printf 'received:%s' \"\$value\""),
                attachStdin = true, attachStdout = true, attachStderr = true, tty = true
            )).getOrThrow().id
            client.exec.startInteractive(execId).getOrThrow().use { session ->
                val ready = CompletableDeferred<Unit>()
                val reader = async {
                    val text = StringBuilder()
                    session.incomingChunks.first {
                        text.append(it.bytes.decodeToString())
                        if (text.contains("ready> ")) ready.complete(Unit)
                        text.contains("received:ok")
                    }
                }
                ready.await()
                session.send("ok\n")
                reader.await()
            }
        } finally {
            client.containers.remove(containerId, force = true, v = true).getOrThrow()
        }
    }

}
