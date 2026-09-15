package dev.limebeck.libs.docker.compose

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.model.ContainerLogsParameters
import dev.limebeck.libs.docker.client.model.ContainerState
import dev.limebeck.libs.docker.client.model.Health
import dev.limebeck.libs.docker.client.model.LogLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class ComposeIntegrationTest {
    @Test
    fun controlsPreserveContainersVolumesAndOtherServices() = runTest(timeout = 60.seconds) {
        DockerClient().use { client ->
            val before = client.compose.getService(FIXTURE_PROJECT, "control").getOrThrow()!!.replicas.single()
            assertTrue(client.compose.start(FIXTURE_PROJECT, "control").getOrThrow().isSuccess)
            val info = client.containers.getInfo(before.id).getOrThrow()
            val token = client.compose.logs(FIXTURE_PROJECT, "control", ContainerLogsParameters(tail = "1"))
                .toList().single().log.line.trim()
            assertTrue(token.isNotBlank())
            val stopped = client.compose.stop(FIXTURE_PROJECT, "control", timeoutSeconds = 0).getOrThrow()
            assertTrue(stopped.isSuccess)
            assertEquals(listOf(before.id), stopped.results.map { it.container.id })
            assertEquals(ContainerState.Status.EXITED, client.containers.getInfo(before.id).getOrThrow().state?.status)
            assertTrue(client.compose.stop(FIXTURE_PROJECT, "control").getOrThrow().isSuccess)
            assertTrue(client.compose.start(FIXTURE_PROJECT, setOf("control")).getOrThrow().isSuccess)
            assertTrue(client.compose.restart(FIXTURE_PROJECT, "control", timeoutSeconds = 0).getOrThrow().isSuccess)
            val after = client.containers.getInfo(before.id).getOrThrow()
            assertEquals(ContainerState.Status.RUNNING, after.state?.status)
            assertEquals(info.mounts, after.mounts)
            val history = client.compose.logs(FIXTURE_PROJECT, "control", ContainerLogsParameters(tail = "all"))
                .toList().map { it.log.line.trim() }.filter { it.isNotBlank() }
            assertTrue(history.size >= 2)
            assertTrue(history.all { it == token }, "Named-volume data must survive stop/start/restart")
            assertTrue(client.compose.getService(FIXTURE_PROJECT, "worker").getOrThrow()!!.replicas
                .all { it.state?.status == ContainerState.Status.RUNNING })
            assertEquals(ContainerState.Status.EXITED,
                client.compose.getService(FIXTURE_PROJECT, "stopped").getOrThrow()!!.replicas.single().state?.status)
        }
    }

    @Test
    fun discoversRealComposeReplicasStoppedOneOffAndIncompleteContainers() = runTest(timeout = 60.seconds) {
        DockerClient().use { client ->
            assertSame(client.compose, client.compose)
            val project = client.compose.getProject(FIXTURE_PROJECT).getOrThrow()!!
            val worker = project.services.single { it.name == "worker" }
            assertEquals(2, worker.replicas.size)
            assertEquals(setOf(1, 2), worker.replicas.map { it.replicaNumber }.toSet())
            assertTrue(worker.replicas.all { it.state?.status == ContainerState.Status.RUNNING })
            assertTrue(worker.replicas.all { it.state?.health?.status == Health.Status.HEALTHY })
            assertEquals(1, worker.containers.count { it.oneOff == true })
            assertEquals(ContainerState.Status.EXITED, project.services.single { it.name == "stopped" }.containers.single().state?.status)
            assertEquals(1, project.unassignedContainers.size)
            assertEquals("$FIXTURE_PROJECT-incomplete", project.unassignedContainers.single().name)
            assertFalse(project.services.flatMap { it.containers }.any { it.name == "$FIXTURE_PROJECT-unrelated" })
            assertNull(client.compose.getProject("$FIXTURE_PROJECT-missing").getOrThrow())
        }
    }

    @Test
    fun realLogsKeepStreamsAndCancelRepeatedlyWithoutClosingClient() = runTest(timeout = 60.seconds) {
        DockerClient().use { client ->
            withContext(Dispatchers.Default) {
                withTimeout(30.seconds) {
                    val history = client.compose.logs(FIXTURE_PROJECT, services = setOf("worker", "stopped"), parameters = ContainerLogsParameters(tail = "10")).toList()
                    assertTrue(history.any { it.service == "worker" && it.oneOff == true && it.log.line.contains("oneoff-out") })
                    assertTrue(history.any { it.service == "stopped" && it.log.type == LogLine.Type.STDERR && it.log.line.contains("stopped-err") })
                    val replicas = client.compose.getService(FIXTURE_PROJECT, "worker").getOrThrow()!!.replicas
                    replicas.forEach { replica ->
                        assertTrue(history.any { it.containerId == replica.id && it.log.type == LogLine.Type.STDOUT })
                        assertTrue(history.any { it.containerId == replica.id && it.log.type == LogLine.Type.STDERR })
                    }
                    repeat(3) {
                        val live = client.compose.logs(FIXTURE_PROJECT, "worker", ContainerLogsParameters(follow = true, tail = "0"), includeOneOff = false)
                            .take(4).toList()
                        assertEquals(4, live.size)
                        assertTrue(live.all { it.oneOff == false && it.project == FIXTURE_PROJECT })
                    }
                    // The integration borrows the client. Repeated collector cancellation leaves it usable.
                    client.containers.getList().getOrThrow()
                }
            }
        }
    }
}
