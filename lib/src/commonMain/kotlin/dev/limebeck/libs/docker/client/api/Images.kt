package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.utils.OciImageRefParser
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*

val DockerClient.images by ::Images.api()

class Images(private val dockerClient: DockerClient) {
    /**
     * List Images
     *
     * Returns a list of images on the server. Note that it uses a different, smaller representation of an image
     * than inspecting a single image.
     */
    suspend fun list(
        all: Boolean = false,
        filters: Map<String, List<String>>? = null,
        sharedSize: Boolean = false,
        digests: Boolean = false,
        manifests: Boolean = false
    ): Result<List<ImageSummary>, ErrorResponse> =
        with(dockerClient) {
            client.get("/images/json") {
                parameter("all", all)
                filters?.let { parameter("filters", json.encodeToString(it)) }
                parameter("shared-size", sharedSize)
                parameter("digests", digests)
                parameter("manifests", manifests)
            }.parse()
        }

    /**
     * Create an image
     *
     * Create an image by either pulling it from a registry or importing it.
     */
    suspend fun create(
        fromImage: String,
        fromSrc: String? = null,
        repo: String? = null,
        tag: String? = null,
        message: String? = null,
        changes: List<String>? = null,
        platform: String? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            val registry = try {
                OciImageRefParser.normalize(fromImage).registry
            } catch (e: IllegalArgumentException) {
                return@with ErrorResponse("Invalid image name: $fromImage: ${e.message}").asError()
            }

            client.post("/images/create") {
                parameter("fromImage", fromImage)

                fromSrc?.let { parameter("fromSrc", it) }
                repo?.let { parameter("repo", it) }
                tag?.let { parameter("tag", it) }
                message?.let { parameter("message", it) }
                changes?.forEach { parameter("changes", it) }
                platform?.let { parameter("platform", it) }

                applyAuthForRegistry(registry)
            }.validateOnly()
        }

    /**
     * Inspect an image
     *
     * Return low-level information about an image.
     */
    suspend fun inspect(
        name: String,
        manifests: Boolean = false
    ): Result<ImageInspect, ErrorResponse> =
        with(dockerClient) {
            client.get("/images/$name/json") {
                parameter("manifests", manifests)
            }.parse()
        }

    /**
     * Get the history of an image
     *
     * Return parent layers of an image.
     */
    suspend fun history(name: String): Result<List<HistoryResponseItem>, ErrorResponse> =
        with(dockerClient) {
            client.get("/images/$name/history").parse()
        }

    /**
     * Push an image
     *
     * Push an image to a registry.
     *
     * If you wish to push an image on to a private registry, that image must already have a tag which references
     * the registry. For example, `registry.example.com/myimage:latest`.
     *
     * The push is then performed by referencing the tag.
     */
    suspend fun push(
        name: String,
        tag: String? = null,
        platform: String? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            val registry = try {
                OciImageRefParser.normalize(name).registry
            } catch (e: IllegalArgumentException) {
                return@with ErrorResponse("Invalid image name: $name: ${e.message}").asError()
            }

            client.post("/images/$name/push") {
                tag?.let { parameter("tag", it) }
                platform?.let { parameter("platform", it) }

                applyAuthForRegistry(registry)
            }.validateOnly()
        }

    /**
     * Tag an image
     *
     * Tag an image so that it becomes part of a repository.
     */
    suspend fun tag(
        name: String,
        repo: String? = null,
        tag: String? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.post("/images/$name/tag") {
                repo?.let { parameter("repo", it) }
                tag?.let { parameter("tag", it) }
            }.validateOnly()
        }

    /**
     * Remove an image
     *
     * Remove an image, along with any untagged parent images that were referenced by that image.
     *
     * Images can't be removed if they have descendant images, are being used by a container, or are being pushed
     * or pulled.
     */
    suspend fun remove(
        name: String,
        force: Boolean = false,
        noPrune: Boolean = false
    ): Result<List<ImageDeleteResponseItem>, ErrorResponse> =
        with(dockerClient) {
            client.delete("/images/$name") {
                parameter("force", force)
                parameter("noprune", noPrune)
            }.parse()
        }

    /**
     * Search images
     *
     * Search for images on Docker Hub.
     */
    suspend fun search(
        term: String,
        limit: Int? = null,
        filters: Map<String, List<String>>? = null
    ): Result<List<ImageSearchResponseItem>, ErrorResponse> =
        with(dockerClient) {
            client.get("/images/search") {
                parameter("term", term)
                limit?.let { parameter("limit", it) }
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Delete unused images
     */
    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<ImagePruneResponse, ErrorResponse> =
        with(dockerClient) {
            client.post("/images/prune") {
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Export an image
     *
     * Get a tarball containing all images and metadata for a repository.
     */
    suspend fun export(
        name: String,
        platform: String? = null
    ): Result<ByteReadChannel, ErrorResponse> =
        with(dockerClient) {
            val response = client.get("/images/$name/get") {
                platform?.let { parameter("platform", it) }
            }
            if (response.status.isSuccess()) {
                response.bodyAsChannel().asSuccess()
            } else {
                json.decodeFromString<ErrorResponse>(response.bodyAsText()).asError()
            }
        }

    /**
     * Export several images
     *
     * Get a tarball containing all images and metadata for several image repositories.
     */
    suspend fun exportAll(
        names: List<String>? = null,
        platform: String? = null
    ): Result<ByteReadChannel, ErrorResponse> =
        with(dockerClient) {
            val response = client.get("/images/get") {
                names?.forEach { parameter("names", it) }
                platform?.let { parameter("platform", it) }
            }
            if (response.status.isSuccess()) {
                response.bodyAsChannel().asSuccess()
            } else {
                json.decodeFromString<ErrorResponse>(response.bodyAsText()).asError()
            }
        }

    /**
     * Import images
     *
     * Load a set of images from a tar archive.
     */
    suspend fun load(
        quiet: Boolean = false,
        body: ByteReadChannel
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.post("/images/load") {
                parameter("quiet", quiet)
                setBody(body)
            }.validateOnly()
        }
}