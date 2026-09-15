package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*

/** Cached volumes API bound to this client and its connection configuration. */
val DockerClient.volumes by ::Volumes.api()

/**
 * Docker volumes operations using the owning [DockerClient].
 *
 * Result-returning methods report daemon HTTP errors as [ErrorResponse]. Transport/decoding failures
 * and cancellation can throw. Live flows report request failures during collection.
 */
class Volumes(private val dockerClient: DockerClient) {
    /**
     * List volumes
     *
     * @param filters Docker filter names mapped to accepted values; encoded as JSON by the SDK.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getList(
        filters: Map<String, List<String>>? = null
    ): Result<VolumeListResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/volumes")) {
                filters?.let {
                    parameter(
                        "filters",
                        json.encodeToString(it)
                    )
                }
            }.parse()
        }

    /**
     * Create a volume
     *
     * @param config Configuration sent to Docker as JSON.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun create(
        config: VolumeCreateOptions = VolumeCreateOptions()
    ): Result<Volume, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/volumes/create")) {
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Inspect a volume
     *
     * @param name Resource name or ID accepted by Docker.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getInfo(name: String): Result<Volume, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/volumes/$name")).parse()
        }

    /**
     * Deletes a volume and its persistent data. Invoke only for an explicit data-deletion action.
     * Container removal does not require deleting its named volumes.
     *
     * @param name Resource name or ID accepted by Docker.
     * @param force Request forced removal; Docker still enforces its resource constraints.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun remove(
        name: String,
        force: Boolean = false
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.delete(apiPath("/volumes/$name")) {
                parameter("force", force.toString())
            }.validateOnly()
        }

    /**
     * Deletes unused volumes selected by Docker and the supplied filters. This can destroy persistent data.
     *
     * @param filters Docker filter names mapped to accepted values; encoded as JSON by the SDK.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<VolumePruneResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/volumes/prune")) {
                filters?.let {
                    parameter(
                        "filters",
                        json.encodeToString(it)
                    )
                }
            }.parse()
        }
}
