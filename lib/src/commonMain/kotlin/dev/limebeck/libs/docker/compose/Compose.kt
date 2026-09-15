package dev.limebeck.libs.docker.compose

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import dev.limebeck.libs.docker.client.model.ContainerLogsParameters
import dev.limebeck.libs.docker.client.model.ContainerSummary
import dev.limebeck.libs.docker.client.model.ErrorResponse
import dev.limebeck.libs.docker.client.model.LogLine
import dev.limebeck.libs.docker.client.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Cached Compose integration using this client's endpoint and lifetime. */
val DockerClient.compose: Compose by api { client: DockerClient -> Compose(client) }

/**
 * Read-only discovery and logs for existing Compose containers. No CLI or source files are needed.
 * The caller owns the supplied client; this integration never closes it.
 * HTTP failures return SDK errors; transport/decoding failures and cancellation propagate.
 * Snapshots are not atomic: a container removed between list and inspect can fail discovery.
 */
class Compose internal constructor(private val source: ComposeSource) {
    constructor(client: DockerClient) : this(EngineComposeSource(client))

    /**
     * Lists all containers, inspects Compose-labeled candidates, and groups them by label values.
     * Includes stopped containers and incomplete labels. Projects without remaining containers
     * (including network/volume-only projects) are not discovered. No label paths are opened.
     */
    suspend fun discover(): Result<ComposeDiscovery, ErrorResponse> {
        val listed = source.list()
        if (listed.isError) return listed.propagateError()
        val containers = listed.getOrThrow().filter { summary ->
            summary.labels.orEmpty().keys.any { it.startsWith(LABEL_PREFIX) }
        }.map { summary ->
            val id = requireNotNull(summary.id?.takeIf { it.isNotBlank() }) { "Compose container has no ID" }
            val inspected = source.inspect(id)
            if (inspected.isError) return inspected.propagateError()
            val info = inspected.getOrThrow()
            val labels = info.config?.labels ?: summary.labels.orEmpty()
            ComposeContainer(
                id = id,
                name = info.name?.removePrefix("/") ?: summary.names?.firstOrNull()?.removePrefix("/"),
                project = labels[PROJECT]?.takeIf { it.isNotBlank() },
                service = labels[SERVICE]?.takeIf { it.isNotBlank() },
                replicaNumber = labels[NUMBER]?.toIntOrNull()?.takeIf { it > 0 },
                oneOff = when (labels[ONE_OFF]?.lowercase()) { "true" -> true; "false" -> false; else -> null },
                image = info.config?.image ?: summary.image,
                state = info.state,
            )
        }
        return Result.success(groupContainers(containers))
    }

    /** Returns the observed project, or successful null if it has no discovered containers. */
    suspend fun getProject(name: String): Result<ComposeProject?, ErrorResponse> {
        require(name.isNotBlank()) { "Project name must not be blank" }
        return discover().map { snapshot -> snapshot.projects.find { it.name == name } }
    }

    /** Returns the observed service, or successful null if no such service was discovered. */
    suspend fun getService(project: String, service: String): Result<ComposeService?, ErrorResponse> {
        require(service.isNotBlank()) { "Service name must not be blank" }
        return getProject(project).map { it?.services?.find { candidate -> candidate.name == service } }
    }

    /** Reads all replicas of one service; equivalent to logs(project, setOf(service), ...). */
    fun logs(
        project: String,
        service: String,
        parameters: ContainerLogsParameters = ContainerLogsParameters(),
        includeOneOff: Boolean = true,
        maxStreams: Int = 64,
    ): Flow<ComposeLogLine> = logs(project, setOf(service), parameters, includeOneOff, maxStreams)

    /**
     * Cold, backpressured flow: discovers once per collection and opens one log stream per selected
     * container. Fails before opening streams if the selection exceeds [maxStreams]. New replicas
     * require recollection. Missing projects/services produce an empty flow. One-off and unknown
     * one-off containers are included by default; false selects only confirmed regular replicas.
     * [services] selects exact service names: null includes all containers (even without service
     * labels); an empty set produces an empty flow without Docker requests. Blank names are rejected.
     * The supplied set is copied when this method is called.
     *
     * Each container preserves record order, but there is no global timestamp ordering. [parameters]
     * (including tail) apply per container. Any discovery/stream failure fails collection and cancels
     * sibling streams; cancellation and consumer failure release all requests. No retry is performed.
     */
    fun logs(
        project: String,
        services: Set<String>? = null,
        parameters: ContainerLogsParameters = ContainerLogsParameters(),
        includeOneOff: Boolean = true,
        maxStreams: Int = 64,
    ): Flow<ComposeLogLine> {
        require(project.isNotBlank()) { "Project name must not be blank" }
        val selectedServices = services?.toSet()
        require(selectedServices == null || selectedServices.none { it.isBlank() }) { "Service names must not be blank" }
        require(maxStreams > 0) { "maxStreams must be positive" }
        return channelFlow {
            if (selectedServices != null && selectedServices.isEmpty()) return@channelFlow
            val snapshot = getProject(project).getOrThrow() ?: return@channelFlow
            val candidates = snapshot.services.flatMap { it.containers } + snapshot.unassignedContainers
            val selected = candidates.filter {
                (selectedServices == null || it.service in selectedServices) && (includeOneOff || it.oneOff == false)
            }
            require(selected.size <= maxStreams) { "Compose log selection exceeds maxStreams" }
            for (container in selected) launch {
                source.logs(container.id, parameters).getOrThrow().collect { line ->
                    send(ComposeLogLine(project, container.service, container.id, container.name,
                        container.replicaNumber, container.oneOff, line))
                }
            }
        }.buffer(0)
    }
}

internal const val LABEL_PREFIX = "com.docker.compose."
internal const val PROJECT = "${LABEL_PREFIX}project"
internal const val SERVICE = "${LABEL_PREFIX}service"
internal const val NUMBER = "${LABEL_PREFIX}container-number"
internal const val ONE_OFF = "${LABEL_PREFIX}oneoff"

internal fun groupContainers(containers: List<ComposeContainer>): ComposeDiscovery {
    val sorted = containers.sortedWith(compareBy<ComposeContainer> { it.replicaNumber ?: Int.MAX_VALUE }.thenBy { it.id })
    val projects = sorted.filter { it.project != null }.groupBy { it.project!! }.toList().sortedBy { it.first }
        .map { (name, members) ->
            ComposeProject(name,
                members.filter { it.service != null }.groupBy { it.service!! }.toList().sortedBy { it.first }
                    .map { (service, replicas) -> ComposeService(service, replicas) },
                members.filter { it.service == null })
        }
    return ComposeDiscovery(projects, sorted.filter { it.project == null })
}

private fun <T> Result<*, ErrorResponse>.propagateError(): Result<T, ErrorResponse> {
    check(isError)
    return map { error("Expected an error result") }
}

internal interface ComposeSource {
    suspend fun list(): Result<List<ContainerSummary>, ErrorResponse>
    suspend fun inspect(id: String): Result<ContainerInspectResponse, ErrorResponse>
    suspend fun logs(id: String, parameters: ContainerLogsParameters): Result<Flow<LogLine>, ErrorResponse>
}

private class EngineComposeSource(private val client: DockerClient) : ComposeSource {
    override suspend fun list() = client.containers.getList(all = true)
    override suspend fun inspect(id: String) = client.containers.getInfo(id)
    override suspend fun logs(id: String, parameters: ContainerLogsParameters) = client.containers.getLogs(id, parameters)
}
