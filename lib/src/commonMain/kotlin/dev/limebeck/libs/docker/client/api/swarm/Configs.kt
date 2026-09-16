package dev.limebeck.libs.docker.client.api.swarm

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Docker Swarm config operations. Requires a Swarm manager; this API never initializes Swarm.
 * HTTP errors return the original SDK Result. Transport/decoding failures and cancellation throw.
 * The caller owns the client. Operations are never retried automatically.
 */
class Configs(private val dockerClient: DockerClient) {
    /** Lists config metadata. Filters accept id, name, names and label, encoded as JSON. */
    suspend fun getList(filters: Map<String, List<String>>? = null): Result<List<Config>, ErrorResponse> =
        with(dockerClient) {
            client.get(apiPath("/configs")) {
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Creates a config. Data in [spec] must already be Base64 encoded; no encoding is performed here.
     * Docker validates payload size, names and driver options. Treat specs and responses as sensitive.
     */
    suspend fun create(spec: ConfigSpec): Result<SwarmCreateResponse, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/configs/create")) {
                contentType(ContentType.Application.Json)
                setBody(spec)
            }.parse()
        }

    /** Inspects a config by ID or name. Config data may be returned; it is not a secret store. */
    suspend fun getInfo(id: String): Result<Config, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.get(apiPath("/configs/${id.encodeURLPathPart()}")).parse()
    }

    /**
     * Updates labels using the [version] index from the latest inspect response.
     * Copy the inspected spec and change only labels; other fields must remain unchanged.
     * Stale versions are reported as Docker errors. No re-inspection, merging or retry is performed.
     */
    suspend fun update(id: String, version: ULong, spec: ConfigSpec): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            require(id.isNotBlank()) { "Resource ID must not be blank" }
            client.post(apiPath("/configs/${id.encodeURLPathPart()}/update")) {
                parameter("version", version.toString())
                contentType(ContentType.Application.Json)
                setBody(spec)
            }.validateOnly()
        }

    /** Deletes a config by ID or name. Docker rejects removal while a service references it. */
    suspend fun remove(id: String): Result<Unit, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.delete(apiPath("/configs/${id.encodeURLPathPart()}")).validateOnly()
    }
}
