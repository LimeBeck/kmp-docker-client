package dev.limebeck.libs.docker.client.api.swarm

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*

/** Manager operations. HTTP failures return Result errors; transport failures and cancellation throw. */
class Nodes(private val dockerClient: DockerClient) {
    /** Lists resources using Docker's JSON filter map. */
    suspend fun getList(filters: Map<String, List<String>>? = null): Result<List<Node>, ErrorResponse> = with(dockerClient) {
        client.get(apiPath("/nodes")) {
            filters?.let { parameter("filters", json.encodeToString(it)) }
        }.parse()
    }

    /** Inspects a resource by ID or name. */
    suspend fun getInfo(id: String): Result<Node, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.get(apiPath("/nodes/${id.encodeURLPathPart()}")).parse()
    }

    /** Removes the resource. Force removal is explicit and defaults to false. */
    suspend fun remove(id: String, force: Boolean = false): Result<Unit, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.delete(apiPath("/nodes/${id.encodeURLPathPart()}")) { parameter("force", force) }.validateOnly()
    }

    /** Replaces the spec using the inspected version; stale versions fail without retry. */
    suspend fun update(id: String, version: ULong, spec: NodeSpec): Result<Unit, ErrorResponse> = with(dockerClient) {
        require(id.isNotBlank()) { "Resource ID must not be blank" }
        client.post(apiPath("/nodes/${id.encodeURLPathPart()}/update")) {
            parameter("version", version.toString())
            contentType(ContentType.Application.Json)
            setBody(spec)
        }.validateOnly()
    }
}
