package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.utils.OciImageRefParser
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.builtins.serializer

/** Cached images API bound to this client and its connection configuration. */
val DockerClient.images by ::Images.api()

/**
 * Docker images operations using the owning [DockerClient].
 *
 * Result-returning methods report daemon HTTP errors as [ErrorResponse]. Transport/decoding failures
 * and cancellation can throw. Live flows report request failures during collection.
 */
class Images(private val dockerClient: DockerClient) {
    /**
     * List Images
     *
     * Returns a list of images on the server. Note that it uses a different, smaller representation of an image
     * than inspecting a single image.
     *
     * @param all Include intermediate image layers.
     * @param filters Docker filter names mapped to accepted values; encoded as JSON by the SDK.
     * @param sharedSize Include size shared with other images.
     * @param digests Include repository digests.
     * @param manifests Request manifest information where supported by the daemon.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun list(
        all: Boolean = false,
        filters: Map<String, List<String>>? = null,
        sharedSize: Boolean = false,
        digests: Boolean = false,
        manifests: Boolean = false
    ): Result<List<ImageSummary>, ErrorResponse> =
        with(dockerClient) {
            client.get(apiPath("/images/json")) {
                parameter("all", all)
                filters?.let { parameter("filters", json.encodeToString(it)) }
                parameter("shared-size", sharedSize)
                parameter("digests", digests)
                parameter("manifests", manifests)
            }.parse()
        }

    /**
     * Pulls/imports an image and consumes the complete progress response.
     * HTTP 200 or a completed layer is not final success: Docker stream errors become an error Result.
     * Callbacks are sequential and apply backpressure; callback exceptions and cancellation propagate.
     * There is no implicit request/idle deadline. Cancelling closes the request without rolling back daemon work.
     *
     * @param fromImage Image reference to pull; also used to select stored registry credentials.
     * @param fromSrc Optional import source forwarded to Docker; this overload does not upload an import body.
     * @param repo Destination repository name.
     * @param tag Image tag; null leaves tag selection to Docker.
     * @param message Optional import commit message.
     * @param changes Dockerfile instructions applied by Docker during import.
     * @param platform Optional Docker platform selector, for example linux/amd64.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
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
        create(fromImage, fromSrc, repo, tag, message, changes, platform, onProgress = {})

    /**
     * Pulls/imports an image and consumes the complete progress response.
     * HTTP 200 or a completed layer is not final success: Docker stream errors become an error Result.
     * Callbacks are sequential and apply backpressure; callback exceptions and cancellation propagate.
     * There is no implicit request/idle deadline. Cancelling closes the request without rolling back daemon work.
     *
     * @param fromImage Image reference to pull; also used to select stored registry credentials.
     * @param fromSrc Optional import source forwarded to Docker; this overload does not upload an import body.
     * @param repo Destination repository name.
     * @param tag Image tag; null leaves tag selection to Docker.
     * @param message Optional import commit message.
     * @param changes Dockerfile instructions applied by Docker during import.
     * @param platform Optional Docker platform selector, for example linux/amd64.
     * @param onProgress Suspending callback invoked sequentially for each progress record.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun create(
        fromImage: String,
        fromSrc: String? = null,
        repo: String? = null,
        tag: String? = null,
        message: String? = null,
        changes: List<String>? = null,
        platform: String? = null,
        onProgress: suspend (ImageProgress<Unit>) -> Unit,
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            val registry = try {
                OciImageRefParser.normalize(fromImage).registry
            } catch (e: IllegalArgumentException) {
                return@with ErrorResponse("Invalid image name: $fromImage: ${e.message}").asError()
            }

            client.preparePost(apiPath("/images/create")) {
                parameter("fromImage", fromImage)

                fromSrc?.let { parameter("fromSrc", it) }
                repo?.let { parameter("repo", it) }
                tag?.let { parameter("tag", it) }
                message?.let { parameter("message", it) }
                changes?.forEach { parameter("changes", it) }
                platform?.let { parameter("platform", it) }

                applyStreamConfig()
                applyAuthForRegistry(registry)
            }.execute { it.validateImageProgress(Unit.serializer(), onProgress) }
        }

    /**
     * Inspect an image
     *
     * Return low-level information about an image.
     *
     * @param name Image reference or ID accepted by Docker.
     * @param manifests Request manifest information where supported by the daemon.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun inspect(
        name: String,
        manifests: Boolean = false
    ): Result<ImageInspect, ErrorResponse> =
        with(dockerClient) {
            client.get(apiPath("/images/$name/json")) {
                parameter("manifests", manifests)
            }.parse()
        }

    /**
     * Get the history of an image
     *
     * Return parent layers of an image.
     *
     * @param name Image reference or ID accepted by Docker.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun history(name: String): Result<List<HistoryResponseItem>, ErrorResponse> =
        with(dockerClient) {
            client.get(apiPath("/images/$name/history")).parse()
        }

    /**
     * Pushes a tagged image to its registry and consumes the complete progress response.
     * HTTP 200 or a completed layer is not final success: Docker stream errors become an error Result.
     * Callbacks are sequential and apply backpressure; callback exceptions and cancellation propagate.
     * There is no implicit request/idle deadline. Cancelling closes the request without rolling back daemon work.
     *
     * @param name Tagged repository reference including its registry, for example registry.example.com/team/app.
     * @param tag Image tag; null leaves tag selection to Docker.
     * @param platform Optional Docker platform selector, for example linux/amd64.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun push(
        name: String,
        tag: String? = null,
        platform: String? = null
    ): Result<Unit, ErrorResponse> =
        push(name, tag, platform, onProgress = {})

    /**
     * Pushes a tagged image to its registry and consumes the complete progress response.
     * HTTP 200 or a completed layer is not final success: Docker stream errors become an error Result.
     * Callbacks are sequential and apply backpressure; callback exceptions and cancellation propagate.
     * There is no implicit request/idle deadline. Cancelling closes the request without rolling back daemon work.
     *
     * @param name Tagged repository reference including its registry, for example registry.example.com/team/app.
     * @param tag Image tag; null leaves tag selection to Docker.
     * @param platform Optional Docker platform selector, for example linux/amd64.
     * @param onProgress Suspending callback invoked sequentially for each progress record.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun push(
        name: String,
        tag: String? = null,
        platform: String? = null,
        onProgress: suspend (ImageProgress<ImagePushResult>) -> Unit,
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            val registry = try {
                OciImageRefParser.normalize(name).registry
            } catch (e: IllegalArgumentException) {
                return@with ErrorResponse("Invalid image name: $name: ${e.message}").asError()
            }

            client.preparePost(apiPath("/images/$name/push")) {
                tag?.let { parameter("tag", it) }
                platform?.let { parameter("platform", it) }

                applyStreamConfig()
                applyAuthForRegistry(registry)
            }.execute { it.validateImageProgress(ImagePushResult.serializer(), onProgress) }
        }

    /**
     * Tag an image
     *
     * Tag an image so that it becomes part of a repository.
     *
     * @param name Image reference or ID accepted by Docker.
     * @param repo Destination repository name.
     * @param tag Image tag; null leaves tag selection to Docker.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun tag(
        name: String,
        repo: String? = null,
        tag: String? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/images/$name/tag")) {
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
     *
     * @param name Image reference or ID accepted by Docker.
     * @param force Request forced removal; Docker still enforces its resource constraints.
     * @param noPrune Retain untagged parent images.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun remove(
        name: String,
        force: Boolean = false,
        noPrune: Boolean = false
    ): Result<List<ImageDeleteResponseItem>, ErrorResponse> =
        with(dockerClient) {
            client.delete(apiPath("/images/$name")) {
                parameter("force", force)
                parameter("noprune", noPrune)
            }.parse()
        }

    /**
     * Search images
     *
     * Search for images on Docker Hub.
     *
     * @param term Docker Hub search term.
     * @param limit Maximum number of entries; null leaves the daemon default.
     * @param filters Docker filter names mapped to accepted values; encoded as JSON by the SDK.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun search(
        term: String,
        limit: Int? = null,
        filters: Map<String, List<String>>? = null
    ): Result<List<ImageSearchResponseItem>, ErrorResponse> =
        with(dockerClient) {
            client.get(apiPath("/images/search")) {
                parameter("term", term)
                limit?.let { parameter("limit", it) }
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Delete unused images
     *
     * @param filters Docker filter names mapped to accepted values; encoded as JSON by the SDK.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<ImagePruneResponse, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/images/prune")) {
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Export an image
     *
     * Get a tarball containing all images and metadata for a repository.
     * Read the returned tar channel to completion or cancel it; use [load] to import an image archive.
     *
     * @param name Image reference or ID accepted by Docker.
     * @param platform Optional Docker platform selector, for example linux/amd64.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun export(
        name: String,
        platform: String? = null
    ): Result<ByteReadChannel, ErrorResponse> =
        with(dockerClient) {
            val response = client.get(apiPath("/images/$name/get")) {
                platform?.let { parameter("platform", it) }
            }
            if (response.status.isSuccess()) {
                response.bodyAsChannel().asSuccess()
            } else {
                response.errorResponse().asError()
            }
        }

    /**
     * Export several images
     *
     * Get a tarball containing all images and metadata for several image repositories.
     * Read the returned tar channel to completion or cancel it; use [load] to import an image archive.
     *
     * @param names Image references to export; null requests all images.
     * @param platform Optional Docker platform selector, for example linux/amd64.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun exportAll(
        names: List<String>? = null,
        platform: String? = null
    ): Result<ByteReadChannel, ErrorResponse> =
        with(dockerClient) {
            val response = client.get(apiPath("/images/get")) {
                names?.forEach { parameter("names", it) }
                platform?.let { parameter("platform", it) }
            }
            if (response.status.isSuccess()) {
                response.bodyAsChannel().asSuccess()
            } else {
                response.errorResponse().asError()
            }
        }

    /**
     * Loads images from a tar archive and consumes the complete progress response.
     * HTTP 200 or a completed layer is not final success: Docker stream errors become an error Result.
     * Callbacks are sequential and apply backpressure; callback exceptions and cancellation propagate.
     * There is no implicit request/idle deadline. Cancelling closes the request without rolling back daemon work.
     *
     * @param quiet Ask Docker to suppress verbose load progress.
     * @param body Tar archive channel consumed by this operation; retries require a fresh source.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun load(
        quiet: Boolean = false,
        body: ByteReadChannel
    ): Result<Unit, ErrorResponse> =
        load(quiet, body, onProgress = {})

    /**
     * Loads images from a tar archive and consumes the complete progress response.
     * HTTP 200 or a completed layer is not final success: Docker stream errors become an error Result.
     * Callbacks are sequential and apply backpressure; callback exceptions and cancellation propagate.
     * There is no implicit request/idle deadline. Cancelling closes the request without rolling back daemon work.
     *
     * @param quiet Ask Docker to suppress verbose load progress.
     * @param body Tar archive channel consumed by this operation; retries require a fresh source.
     * @param onProgress Suspending callback invoked sequentially for each progress record.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun load(
        quiet: Boolean = false,
        body: ByteReadChannel,
        onProgress: suspend (ImageProgress<Unit>) -> Unit,
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            client.preparePost(apiPath("/images/load")) {
                applyStreamConfig()
                parameter("quiet", quiet)
                contentType(ContentType("application", "x-tar"))
                setBody(body)
            }.execute { it.validateImageProgress(Unit.serializer(), onProgress) }
        }
}
