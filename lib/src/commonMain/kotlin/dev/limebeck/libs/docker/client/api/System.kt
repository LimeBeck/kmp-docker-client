package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

val DockerClient.system by ::System.api()

class System(private val dockerClient: DockerClient) {
    /**
     * Get system information
     */
    suspend fun getInfo(): Result<SystemInfo, ErrorResponse> =
        with(dockerClient) {
            return client.get("/info").parse()
        }

    /**
     * Get version
     *
     * Returns the version of Docker that is running and various information about the system that Docker is running on.
     */
    suspend fun getVersion(): Result<SystemVersion, ErrorResponse> =
        with(dockerClient) {
            return client.get("/version").parse()
        }

    /**
     * Ping
     *
     * This is a dummy endpoint you can use to test if the server is accessible.
     */
    suspend fun ping(): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.get("/_ping").validateOnly()
        }

    /**
     * Get data usage information
     */
    suspend fun dataUsage(): Result<SystemDataUsageResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get("/system/df").parse()
        }

    /**
     * Monitor events
     *
     * Stream real-time events from the server.
     *
     * Various objects within Docker report events when something happens to them.
     *
     * Containers report these events: `attach`, `commit`, `copy`, `create`, `destroy`, `detach`, `die`, `exec_create`, `exec_detach`, `exec_start`, `exec_die`, `export`, `health_status`, `kill`, `oom`, `pause`, `rename`, `resize`, `restart`, `start`, `stop`, `top`, `unpause`, `update`, and `prune`
     *
     * Images report these events: `create`, `delete`, `import`, `load`, `pull`, `push`, `save`, `tag`, `untag`, and `prune`
     *
     * Volumes report these events: `create`, `mount`, `unmount`, `destroy`, and `prune`
     *
     * Networks report these events: `create`, `connect`, `disconnect`, `destroy`, `update`, `remove`, and `prune`
     *
     * The Docker daemon reports these events: `reload`
     *
     * Services report these events: `create`, `update`, and `remove`
     *
     * Nodes report these events: `create`, `update`, and `remove`
     *
     * Secrets report these events: `create`, `update`, and `remove`
     *
     * Configs report these events: `create`, `update`, and `remove`
     *
     * The Builder reports `prune` events
     *
     * @param since Show events created since this timestamp then stream new events.
     * @param until Show events created until this timestamp then stop streaming.
     * @param filters A JSON encoded value of filters (a `map[string][]string`) to process on the event list.
     */
    fun events(
        since: String? = null,
        until: String? = null,
        filters: Map<String, List<String>>? = null
    ): Flow<EventMessage> = with(dockerClient) {
        flow {
            client.prepareGet("/events") {
                since?.let { parameter("since", it) }
                until?.let { parameter("until", it) }
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.execute { response ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line()
                    if (!line.isNullOrBlank()) {
                        try {
                            emit(json.decodeFromString<EventMessage>(line))
                        } catch (e: Exception) {
                            // Skip invalid lines
                        }
                    }
                }
            }
        }
    }
}
