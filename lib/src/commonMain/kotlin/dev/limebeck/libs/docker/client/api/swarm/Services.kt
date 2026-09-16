package dev.limebeck.libs.docker.client.api.swarm

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow

/** Manager operations. HTTP failures return Result errors; transport failures and cancellation throw. */
class Services(private val dockerClient: DockerClient) {
    /** Lists resources using Docker's JSON filter map. */
    suspend fun getList(filters: Map<String, List<String>>? = null, status: Boolean = false): Result<List<Service>, ErrorResponse> = with(dockerClient) {
        client.get(apiPath("/services")) {
            filters?.let { parameter("filters", json.encodeToString(it)) }
            parameter("status", status)
        }.parse()
    }

    /** Inspects a resource by ID or name. */
    suspend fun getInfo(id: String, insertDefaults: Boolean = false): Result<Service, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.get(apiPath("/services/${id.encodeURLPathPart()}")) { parameter("insertDefaults", insertDefaults) }.parse()
    }

    /** Removes the resource. Docker asynchronously removes its tasks. */
    suspend fun remove(id: String): Result<Unit, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.delete(apiPath("/services/${id.encodeURLPathPart()}")).validateOnly()
    }

    /** Creates a service; success does not mean tasks are ready. Auth is already Base64url encoded. */
    suspend fun create(spec: ServiceSpec, registryAuth: String? = null): Result<ServiceCreateResponse, ErrorResponse> = with(dockerClient) {
        client.post(apiPath("/services/create")) {
            registryAuth?.let { header("X-Registry-Auth", it) }
            contentType(ContentType.Application.Json)
            setBody(spec)
        }.parse()
    }

    /**
     * Replaces the spec using its inspected version. No merge, retry or readiness wait is performed.
     * Set rollback to "previous" for server-side rollback. Pass a complete valid spec even for rollback.
     * registryAuthFrom selects "spec" or "previous-spec" when registryAuth is absent.
     */
    suspend fun update(
        id: String, version: ULong, spec: ServiceSpec,
        registryAuth: String? = null, registryAuthFrom: String? = null, rollback: String? = null,
    ): Result<ServiceUpdateResponse, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        require(registryAuthFrom == null || registryAuthFrom in setOf("spec", "previous-spec"))
        require(rollback == null || rollback == "previous")
        client.post(apiPath("/services/${id.encodeURLPathPart()}/update")) {
            parameter("version", version.toString())
            registryAuthFrom?.let { parameter("registryAuthFrom", it) }
            rollback?.let { parameter("rollback", it) }
            registryAuth?.let { header("X-Registry-Auth", it) }
            contentType(ContentType.Application.Json)
            setBody(spec)
        }.parse()
    }

    /**
     * Inspects TTY framing now, then returns a cold log flow. Collection opens the log request.
     * Collection HTTP errors throw DockerApiException; cancellation/completion closes the request.
     * Docker requires a supported logging driver. No retry or ordering across tasks is added.
     */
    suspend fun getLogs(id: String, parameters: SwarmLogsParameters = SwarmLogsParameters()): Result<Flow<LogLine>, ErrorResponse> =
        getInfo(id).map { dockerClient.swarmLogs("/services/${id.encodeURLPathPart()}/logs", it.spec?.taskTemplate?.containerSpec?.TTY == true, parameters) }
}
