package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*

/** Cached networks API bound to this client and its connection configuration. */
val DockerClient.networks by ::Networks.api()

/**
 * Docker networks operations using the owning [DockerClient].
 *
 * Result-returning methods report daemon HTTP errors as [ErrorResponse]. Transport/decoding failures
 * and cancellation can throw. Live flows report request failures during collection.
 */
class Networks(private val dockerClient: DockerClient) {
    /**
     * List networks
     *
     * Returns a list of networks.
     *
     * @param filters Docker filter names mapped to accepted values; encoded as JSON by the SDK.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun list(
        filters: Map<String, List<String>>? = null
    ): Result<List<Network>, ErrorResponse> =
        with(dockerClient) {
            client.get(apiPath("/networks")) {
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Inspect a network
     *
     * Return low-level information about a network.
     *
     * @param id Network ID or name.
     * @param verbose Include detailed network diagnostics.
     * @param scope Optional Docker network scope filter.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun inspect(
        id: String,
        verbose: Boolean = false,
        scope: String? = null
    ): Result<Network, ErrorResponse> =
        with(dockerClient) {
            client.get(apiPath("/networks/$id")) {
                parameter("verbose", verbose)
                scope?.let { parameter("scope", it) }
            }.parse()
        }

    /**
     * Create a network
     *
     * @param networkConfig Network name, driver, IPAM and other creation options.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun create(
        networkConfig: NetworkCreateRequest
    ): Result<NetworkCreateResponse, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/networks/create")) {
                contentType(ContentType.Application.Json)
                setBody(networkConfig)
            }.parse()
        }

    /**
     * Deletes a network; disconnect attached containers first when required by Docker.
     *
     * @param id Network ID or name.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun remove(id: String): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.delete(apiPath("/networks/$id")).validateOnly()
        }

    /**
     * Connect a container to a network
     *
     * @param id Network ID or name.
     * @param connectionConfig Container and endpoint options sent to Docker.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun connect(
        id: String,
        connectionConfig: NetworkConnectRequest
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/networks/$id/connect")) {
                contentType(ContentType.Application.Json)
                setBody(connectionConfig)
            }.validateOnly()
        }

    /**
     * Disconnect a container from a network
     *
     * @param id Network ID or name.
     * @param connectionConfig Container and endpoint options sent to Docker.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun disconnect(
        id: String,
        connectionConfig: NetworkDisconnectRequest
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/networks/$id/disconnect")) {
                contentType(ContentType.Application.Json)
                setBody(connectionConfig)
            }.validateOnly()
        }

    /**
     * Delete unused networks
     *
     * @param filters Docker filter names mapped to accepted values; encoded as JSON by the SDK.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<NetworkPruneResponse, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/networks/prune")) {
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }
}
