package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*

val DockerClient.networks by ::Networks.api()

class Networks(private val dockerClient: DockerClient) {
    /**
     * List networks
     *
     * Returns a list of networks.
     */
    suspend fun list(
        filters: Map<String, List<String>>? = null
    ): Result<List<Network>, ErrorResponse> =
        with(dockerClient) {
            client.get("/networks") {
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Inspect a network
     *
     * Return low-level information about a network.
     */
    suspend fun inspect(
        id: String,
        verbose: Boolean = false,
        scope: String? = null
    ): Result<Network, ErrorResponse> =
        with(dockerClient) {
            client.get("/networks/$id") {
                parameter("verbose", verbose)
                scope?.let { parameter("scope", it) }
            }.parse()
        }

    /**
     * Create a network
     */
    suspend fun create(
        networkConfig: NetworkCreateRequest
    ): Result<NetworkCreateResponse, ErrorResponse> =
        with(dockerClient) {
            client.post("/networks/create") {
                contentType(ContentType.Application.Json)
                setBody(networkConfig)
            }.parse()
        }

    /**
     * Remove a network
     */
    suspend fun remove(id: String): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.delete("/networks/$id").validateOnly()
        }

    /**
     * Connect a container to a network
     */
    suspend fun connect(
        id: String,
        connectionConfig: NetworkConnectRequest
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.post("/networks/$id/connect") {
                contentType(ContentType.Application.Json)
                setBody(connectionConfig)
            }.validateOnly()
        }

    /**
     * Disconnect a container from a network
     */
    suspend fun disconnect(
        id: String,
        connectionConfig: NetworkDisconnectRequest
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.post("/networks/$id/disconnect") {
                contentType(ContentType.Application.Json)
                setBody(connectionConfig)
            }.validateOnly()
        }

    /**
     * Delete unused networks
     */
    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<NetworkPruneResponse, ErrorResponse> =
        with(dockerClient) {
            client.post("/networks/prune") {
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }
}
