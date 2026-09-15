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
