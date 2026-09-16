package dev.limebeck.libs.docker.client.api.swarm

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Docker Swarm secret operations. Requires a Swarm manager; this API never initializes Swarm.
 * HTTP errors return the original SDK Result. Transport/decoding failures and cancellation throw.
 * The caller owns the client. Operations are never retried automatically.
 */
class Secrets(private val dockerClient: DockerClient) {
    /** Lists secret metadata. Filters accept id, name, names and label, encoded as JSON. */
    suspend fun getList(filters: Map<String, List<String>>? = null): Result<List<Secret>, ErrorResponse> =
        with(dockerClient) {
            client.get(apiPath("/secrets")) {
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Creates a secret. Data in [spec] must already be Base64 encoded; no encoding is performed here.
     * Docker validates payload size, names and driver options. Treat specs and responses as sensitive.
     */
    suspend fun create(spec: SecretSpec): Result<SwarmCreateResponse, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/secrets/create")) {
                contentType(ContentType.Application.Json)
                setBody(spec)
            }.parse()
        }

    /** Inspects a secret by ID or name. Docker never returns secret data. */
    suspend fun getInfo(id: String): Result<Secret, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.get(apiPath("/secrets/${id.encodeURLPathPart()}")).parse()
    }

    /**
     * Updates labels using the [version] index from the latest inspect response.
     * Copy the inspected spec and change only labels; other fields must remain unchanged.
     * Stale versions are reported as Docker errors. No re-inspection, merging or retry is performed.
     */
    suspend fun update(id: String, version: ULong, spec: SecretSpec): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            require(id.isNotBlank()) { "Resource ID must not be blank" }
            client.post(apiPath("/secrets/${id.encodeURLPathPart()}/update")) {
                parameter("version", version.toString())
                contentType(ContentType.Application.Json)
                setBody(spec)
            }.validateOnly()
        }

    /** Deletes a secret by ID or name. Docker rejects removal while a service references it. */
    suspend fun remove(id: String): Result<Unit, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.delete(apiPath("/secrets/${id.encodeURLPathPart()}")).validateOnly()
    }
}
