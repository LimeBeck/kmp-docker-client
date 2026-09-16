package dev.limebeck.libs.docker.client.api.swarm

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow

/** Manager operations. HTTP failures return Result errors; transport failures and cancellation throw. */
class Tasks(private val dockerClient: DockerClient) {
    /** Lists resources using Docker's JSON filter map. */
    suspend fun getList(filters: Map<String, List<String>>? = null): Result<List<Task>, ErrorResponse> = with(dockerClient) {
        client.get(apiPath("/tasks")) {
            filters?.let { parameter("filters", json.encodeToString(it)) }
        }.parse()
    }

    /** Inspects a resource by ID. */
    suspend fun getInfo(id: String): Result<Task, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.get(apiPath("/tasks/${id.encodeURLPathPart()}")).parse()
    }

    /**
     * Inspects TTY framing now, then returns a cold log flow. Collection opens the log request.
     * Collection HTTP errors throw DockerApiException; cancellation/completion closes the request.
     * Docker requires a supported logging driver. No retry or ordering across tasks is added.
     */
    suspend fun getLogs(id: String, parameters: SwarmLogsParameters = SwarmLogsParameters()): Result<Flow<LogLine>, ErrorResponse> =
        getInfo(id).map { dockerClient.swarmLogs("/tasks/${id.encodeURLPathPart()}/logs", it.spec?.containerSpec?.TTY == true, parameters) }
}
