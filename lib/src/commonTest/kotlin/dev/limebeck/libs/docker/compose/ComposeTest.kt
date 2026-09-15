package dev.limebeck.libs.docker.compose

import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.model.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ComposeTest {
    @Test
    fun discoveryPreservesHealthReplicasAndIncompleteLabels() = runTest {
        val source = FakeSource()
        source.add("replica2", labels(number = "2"), ContainerState(status = ContainerState.Status.RUNNING,
            health = Health(status = Health.Status.UNHEALTHY)))
        source.add("replica1", labels(number = "1"), ContainerState(status = ContainerState.Status.EXITED))
        source.add("job", labels(oneOff = "True"))
        source.add("unknown", labels(number = "bad", oneOff = "invalid"))
        source.add("noService", mapOf(PROJECT to "demo"))
        source.add("noProject", mapOf(SERVICE to "worker"))
        source.add("unrelated", emptyMap())
        source.add("blankProject", mapOf(PROJECT to " ", SERVICE to "worker"))
        val snapshot = Compose(source).discover().getOrThrow()
        assertEquals(listOf("demo"), snapshot.projects.map { it.name })
        val project = snapshot.projects.single()
        assertEquals(listOf("noService"), project.unassignedContainers.map { it.id })
        assertEquals(setOf("noProject", "blankProject"), snapshot.unassignedContainers.map { it.id }.toSet())
        val service = project.services.single()
        assertEquals(listOf("replica1", "replica2"), service.replicas.map { it.id })
        assertEquals(ContainerState.Status.EXITED, service.replicas[0].state?.status)
        assertEquals(Health.Status.UNHEALTHY, service.replicas[1].state?.health?.status)
        assertNull(service.containers.find { it.id == "unknown" }!!.oneOff)
        assertNull(service.containers.find { it.id == "unknown" }!!.replicaNumber)
        assertFalse("unrelated" in source.inspected)
    }

    @Test
    fun exactLookupDoesNotInferMembershipFromContainerName() = runTest {
        val source = FakeSource()
        source.add("demo-worker-1", emptyMap())
        source.add("real", labels())
        source.add("other", labels().plus(PROJECT to "demo-other"))
        val api = Compose(source)
        assertEquals(listOf("real"), api.getService("demo", "worker").getOrThrow()!!.containers.map { it.id })
        assertNull(api.getProject("missing").getOrThrow())
        assertNull(api.getService("demo", "missing").getOrThrow())
    }

    @Test
    fun discoveryReturnsListAndInspectErrorsWithoutRepacking() = runTest {
        val source = FakeSource()
        val failure = ErrorResponse("failure")
        source.listFailure = Result.error(failure)
        assertSame(failure, Compose(source).discover().errorOrNull())
        source.listFailure = null
        source.add("gone", labels())
        source.inspectFailure = Result.error(failure)
        assertSame(failure, Compose(source).discover().errorOrNull())
    }

    @Test
    fun logsAreColdAndKeepIdentityAndPerContainerOrder() = runTest {
        val source = FakeSource()
        source.add("a", labels())
        source.add("b", labels(number = "2", oneOff = "True"))
        source.add("other", labels().plus(PROJECT to "elsewhere"))
        source.logFlow = { id -> flowOf(LogLine(LogLine.Type.STDOUT, "$id-out"), LogLine(LogLine.Type.STDERR, "$id-err")) }
        val options = ContainerLogsParameters(tail = "5", timestamps = true)
        val stream = Compose(source).logs("demo", parameters = options)
        assertEquals(0, source.lists)
        val lines = stream.toList()
        assertEquals(setOf("a", "b"), lines.map { it.containerId }.toSet())
        lines.groupBy { it.containerId }.forEach { (id, records) ->
            assertEquals(listOf("$id-out", "$id-err"), records.map { it.log.line })
            assertEquals(listOf(LogLine.Type.STDOUT, LogLine.Type.STDERR), records.map { it.log.type })
            assertTrue(records.all { it.project == "demo" && it.service == "worker" })
        }
        assertTrue(source.options.all { it == options })
        assertEquals(listOf("a", "a"), Compose(source).logs("demo", includeOneOff = false).toList().map { it.containerId })
        assertTrue(Compose(source).logs("demo", services = setOf("missing")).toList().isEmpty())
    }

    @Test
    fun multipleServicesSelectAllTheirReplicasAndSnapshotTheSet() = runTest {
        val source = FakeSource()
        source.add("worker1", labels())
        source.add("worker2", labels(number = "2"))
        source.add("api1", labels().plus(SERVICE to "api"))
        source.add("db1", labels().plus(SERVICE to "db"))
        source.add("unassigned", mapOf(PROJECT to "demo"))
        source.add("otherProject", labels().plus(PROJECT to "other"))
        source.logFlow = { flowOf(LogLine(LogLine.Type.STDOUT, it)) }
        val services = mutableSetOf("worker", "api", "missing")
        val stream = Compose(source).logs("demo", services = services)
        services.clear()
        assertEquals(setOf("worker1", "worker2", "api1"), stream.toList().map { it.containerId }.toSet())
        assertEquals(setOf("api1"), Compose(source).logs("demo", setOf("api")).toList().map { it.containerId }.toSet())
        assertEquals(setOf("worker1", "worker2", "api1", "db1", "unassigned"),
            Compose(source).logs("demo").toList().map { it.containerId }.toSet())
    }

    @Test
    fun singleServiceOverloadPreservesOptionsAndLimits() = runTest {
        val source = FakeSource()
        source.add("replica", labels())
        source.add("oneoff", labels(oneOff = "True"))
        source.add("api", labels().plus(SERVICE to "api"))
        source.logFlow = { flowOf(LogLine(LogLine.Type.STDOUT, it)) }
        val parameters = ContainerLogsParameters(tail = "3", timestamps = true)
        val api = Compose(source)
        assertEquals(listOf("replica"), api.logs("demo", service = "worker", parameters = parameters,
            includeOneOff = false, maxStreams = 1).toList().map { it.containerId })
        assertEquals(listOf(parameters), source.options)
        assertFailsWith<IllegalArgumentException> { api.logs("demo", "worker", maxStreams = 1).toList() }
        assertFailsWith<IllegalArgumentException> { api.logs("demo", " ") }
    }

    @Test
    fun emptyServiceSelectionDoesNotContactDockerAndBlankNamesAreRejected() = runTest {
        val source = FakeSource()
        source.listFailure = Result.error(ErrorResponse("Docker unavailable"))
        val api = Compose(source)
        assertTrue(api.logs("demo", emptySet()).toList().isEmpty())
        assertEquals(0, source.lists)
        assertTrue(source.options.isEmpty())
        assertFailsWith<IllegalArgumentException> { api.logs("demo", setOf("worker", " ")) }
    }

    @Test
    fun streamLimitRejectsBeforeOpeningAnyLogs() = runTest {
        val source = FakeSource()
        source.add("a", labels())
        source.add("b", labels())
        assertFailsWith<IllegalArgumentException> { Compose(source).logs("demo", maxStreams = 1).toList() }
        assertTrue(source.options.isEmpty())
    }

    @Test
    fun cancellationAndConsumerFailureCloseAllProducers() = runTest {
        val source = FakeSource()
        source.add("a", labels())
        source.add("b", labels())
        source.logFlow = { flow {
            source.active++
            try { while (true) emit(LogLine(LogLine.Type.UNKNOWN, "tty")) }
            finally { source.active-- }
        } }
        val api = Compose(source)
        repeat(3) {
            assertEquals(1, api.logs("demo").take(1).toList().size)
            assertEquals(0, source.active)
        }
        val failure = IllegalArgumentException("consumer")
        val caught = assertFailsWith<IllegalArgumentException> {
            api.logs("demo").collect { throw failure }
        }
        assertEquals(failure.message, caught.message)
        assertTrue(generateSequence<Throwable>(caught) { it.cause }.any { it === failure })
        assertEquals(0, source.active)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun slowConsumerBackpressuresEveryProducer() = runTest {
        val source = FakeSource()
        source.add("a", labels())
        source.add("b", labels())
        var produced = 0
        source.logFlow = { flow {
            source.active++
            try {
                while (true) {
                    produced++
                    emit(LogLine(LogLine.Type.STDOUT, "record"))
                }
            } finally { source.active-- }
        } }
        val received = CompletableDeferred<Unit>()
        val collector = backgroundScope.launch {
            Compose(source).logs("demo").collect { received.complete(Unit); awaitCancellation() }
        }
        runCurrent()
        assertTrue(received.isCompleted)
        assertEquals(2, source.active)
        assertTrue(produced <= 3, "Only the consumed record and one pending record per producer are allowed")
        collector.cancelAndJoin()
        assertEquals(0, source.active)
    }

    @Test
    fun streamFailureCancelsAnAlreadyActiveSibling() = runTest {
        val source = FakeSource()
        source.add("a", labels())
        source.add("b", labels())
        val started = CompletableDeferred<Unit>()
        val failure = IllegalStateException("stream failure")
        source.logFlow = { id -> flow {
            if (id == "a") {
                source.active++
                try { started.complete(Unit); awaitCancellation() } finally { source.active-- }
            } else {
                started.await()
                throw failure
            }
        } }
        val caught = assertFailsWith<IllegalStateException> { Compose(source).logs("demo").toList() }
        assertEquals(failure.message, caught.message)
        assertTrue(generateSequence<Throwable>(caught) { it.cause }.any { it === failure })
        assertEquals(0, source.active)
    }

    @Test
    fun cancellationDuringDiscoveryPropagates() = runTest {
        val source = FakeSource()
        source.add("a", labels())
        val cancellation = kotlinx.coroutines.CancellationException("cancel discovery")
        source.inspectThrow = cancellation
        assertSame(cancellation, assertFailsWith<kotlinx.coroutines.CancellationException> { Compose(source).discover() })
    }
}

private fun labels(number: String = "1", oneOff: String = "False") =
    mapOf(PROJECT to "demo", SERVICE to "worker", NUMBER to number, ONE_OFF to oneOff)

private class FakeSource : ComposeSource {
    val summaries = mutableListOf<ContainerSummary>()
    val inspections = mutableMapOf<String, ContainerInspectResponse>()
    val inspected = mutableListOf<String>()
    val options = mutableListOf<ContainerLogsParameters>()
    var lists = 0
    var active = 0
    var listFailure: Result<List<ContainerSummary>, ErrorResponse>? = null
    var inspectFailure: Result<ContainerInspectResponse, ErrorResponse>? = null
    var inspectThrow: Throwable? = null
    var logFlow: (String) -> Flow<LogLine> = { emptyFlow() }
    fun add(id: String, labels: Map<String, String>, state: ContainerState? = null) {
        summaries += ContainerSummary(id = id, labels = labels, names = listOf("/$id"))
        inspections[id] = ContainerInspectResponse(id = id, name = "/$id", config = ContainerConfig(labels = labels), state = state)
    }
    override suspend fun list(): Result<List<ContainerSummary>, ErrorResponse> {
        lists++
        return listFailure ?: Result.success(summaries)
    }
    override suspend fun inspect(id: String): Result<ContainerInspectResponse, ErrorResponse> {
        inspected += id
        inspectThrow?.let { throw it }
        return inspectFailure ?: Result.success(inspections.getValue(id))
    }
    override suspend fun logs(id: String, parameters: ContainerLogsParameters): Result<Flow<LogLine>, ErrorResponse> {
        options += parameters
        return Result.success(logFlow(id))
    }
}
