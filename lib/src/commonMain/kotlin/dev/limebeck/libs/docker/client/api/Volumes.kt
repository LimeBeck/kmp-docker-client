package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*

val DockerClient.volumes by ::Volumes.api()

class Volumes(private val dockerClient: DockerClient) {
    /**
     * List volumes
     */
    suspend fun getList(
        filters: Map<String, List<String>>? = null
    ): Result<VolumeListResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get("/volumes") {
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
     */
    suspend fun create(
        config: VolumeCreateOptions = VolumeCreateOptions()
    ): Result<Volume, ErrorResponse> =
        with(dockerClient) {
            return client.post("/volumes/create") {
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Inspect a volume
     *
     * @param name Volume name or ID
     */
    suspend fun getInfo(name: String): Result<Volume, ErrorResponse> =
        with(dockerClient) {
            return client.get("/volumes/$name").parse()
        }

    /**
     * Remove a volume
     *
     * @param name Volume name or ID
     * @param force Force the removal of the volume
     */
    suspend fun remove(
        name: String,
        force: Boolean = false
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.delete("/volumes/$name") {
                parameter("force", force.toString())
            }.validateOnly()
        }

    /**
     * Delete unused volumes
     */
    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<VolumePruneResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post("/volumes/prune") {
                filters?.let {
                    parameter(
                        "filters",
                        json.encodeToString(it)
                    )
                }
            }.parse()
        }
}
