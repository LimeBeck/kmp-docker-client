package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.utils.readDockerLine

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.utils.createInteractiveSession
import dev.limebeck.libs.docker.client.utils.readLogLines
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.buffer

/** Cached containers API bound to this client and its connection configuration. */
val DockerClient.containers by ::Containers.api()

/**
 * Docker containers operations using the owning [DockerClient].
 *
 * Result-returning methods report daemon HTTP errors as [ErrorResponse]. Transport/decoding failures
 * and cancellation can throw. Live flows report request failures during collection.
 */
class Containers(private val dockerClient: DockerClient) {
    /**
     * List containers
     *
     * Returns a list of containers. For details on the format, see the [getInfo].
     * Note that it uses a different, smaller representation of a container than inspecting a single container.
     * For example, the list of linked containers is not propagated.
     *
     * @param all Include stopped containers; false lists running containers.
     * @param limit Maximum number of entries; null leaves the daemon default.
     * @param size Include container filesystem sizes.
     * @param filters Docker filter names mapped to values, for example mapOf("status" to listOf("running"))
     * for listing, or mapOf("label" to listOf("app=worker")). Allowed keys depend on the operation;
     * the SDK encodes the map as JSON and Docker validates it.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getList(
        all: Boolean = false,
        limit: Int? = null,
        size: Boolean = false,
        filters: Map<String, List<String>>? = null,
    ): Result<List<ContainerSummary>, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/containers/json")) {
                parameter("all", all.toString())
                parameter("size", size.toString())
                limit?.let { parameter("limit", it.toString()) }
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.parse()
        }

    /**
     * Inspect a container
     *
     * Return low-level information about a container.
     *
     * @param id Container ID or name.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getInfo(id: String): Result<ContainerInspectResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/containers/$id/json")).parse()
        }

    /**
     * Inspects the container immediately to determine TTY framing, then returns a cold flow.
     * The log request opens during collection. HTTP errors then throw [dev.limebeck.libs.docker.client.model.DockerApiException];
     * transport, decoding and collector failures propagate. Cancellation or completion releases the request.
     * Follow mode has no implicit timeout; the caller owns its deadline and resubscription policy.
     *
     * @param id Container ID or name.
     * @sample dev.limebeck.libs.docker.guide.observeForThirtySeconds
     * @param parameters Log streams, history bounds and follow mode; see [ContainerLogsParameters] for units/defaults.
     * @return Prepared flow or an error encountered before collection; see collection failure semantics above.
     */
    suspend fun getLogs(
        id: String,
        parameters: ContainerLogsParameters = ContainerLogsParameters()
    ): Result<Flow<LogLine>, ErrorResponse> =
        with(dockerClient) {
            coroutineScope {
                val inspection = getInfo(id)
                inspection.errorResultOrNull()?.let { return@coroutineScope it }
                val container = inspection.getOrThrow()

                val logs = channelFlow {
                    client.prepareGet(apiPath("/containers/${id}/logs")) {

                        parameter("follow", parameters.follow.toString())
                        parameter("timestamps", parameters.timestamps.toString())
                        parameter("stdout", parameters.stdout.toString())
                        parameter("stderr", parameters.stderr.toString())

                        parameters.until?.let { parameter("until", it) }
                        parameters.since?.let { parameter("since", it) }
                        parameters.tail?.let { parameter("tail", it) }

                        applyStreamConfig()
                    }.execute {
                        it.consumeStream { channel ->
                            channel.readLogLines(container.config?.tty == true) { send(it) }
                        }
                    }
                }.buffer(0)

                return@coroutineScope logs.asSuccess()
            }
        }

    /**
     * Creates but does not start the container. Pull the image first if absent.
     * A name conflict is returned as an error; existing resources are not replaced.
     *
     * @see start
     * @sample dev.limebeck.libs.docker.guide.createWorker
     * @param name New container name; null lets Docker generate one. A conflicting name is an error.
     * @param config Container image, command and environment. Commands are argument lists, not shell
     * expressions: use listOf("sh", "-c", "...") explicitly when shell expansion is needed.
     * @return Created container ID and daemon warnings, or a Docker error. Transport/decoding failures
     * and cancellation throw; success does not mean the container has started.
     */
    suspend fun create(
        name: String? = null,
        config: ContainerConfig = ContainerConfig()
    ): Result<ContainerCreateResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/create")) {
                name?.let { parameter("name", it) }
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Creates but does not start the container. Pull the image first if absent.
     * A name conflict is returned as an error; existing resources are not replaced.
     *
     * hostConfig configures mounts, published ports and resource limits; networkingConfig selects
     * initial network attachments. Declaring exposedPorts alone does not publish a host port.
     *
     * @see start
     * @see ContainerCreateRequest
     * @see HostConfig
     * @sample dev.limebeck.libs.docker.guide.createService
     * @param name New container name; null lets Docker generate one. A conflicting name is an error.
     * @param config Container image, command and environment. Commands are argument lists, not shell
     * expressions: use listOf("sh", "-c", "...") explicitly when shell expansion is needed.
     * @return Created container ID and daemon warnings, or a Docker error. Transport/decoding failures
     * and cancellation throw; success does not mean the container has started.
     */
    suspend fun create(
        name: String? = null,
        config: ContainerCreateRequest,
    ): Result<ContainerCreateResponse, ErrorResponse> =
        with(dockerClient) {
            client.post(apiPath("/containers/create")) {
                name?.let { parameter("name", it) }
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Starts an already-created container. Success means Docker accepted the start, not that the
     * application is ready. A failed start does not remove the container.
     *
     * @see create
     * @see wait
     *
     * @param id Container ID or name.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun start(id: String): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/start"))
                .validateOnly()
        }

    /**
     * Stop a container
     *
     * @param id Container ID or name.
     * @param signal Stop/kill signal; null leaves the daemon default.
     * @param t Seconds to wait before forcibly killing the container; null uses the daemon default.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun stop(
        id: String,
        signal: String? = null,
        t: Int? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/stop")) {
                signal?.let { parameter("signal", signal) }
                t?.let { parameter("t", t.toString()) }
            }.validateOnly()
        }

    /**
     * Remove a container
     *
     * @param id Container ID or name.
     * @param force Request forced removal; Docker still enforces its resource constraints.
     * @param link Remove the specified container link instead of the container.
     * @param v Remove associated anonymous volumes; named volumes are retained.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun remove(
        id: String,
        force: Boolean = false,
        link: Boolean = false,
        v: Boolean = false
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.delete(apiPath("/containers/$id")) {
                parameter("force", force.toString())
                parameter("link", link.toString())
                parameter("v", v.toString())
            }.validateOnly()
        }

    /**
     * Restart a container
     *
     * @param id Container ID or name.
     * @param signal Stop/kill signal; null leaves the daemon default.
     * @param t Seconds to wait before forcibly killing the container; null uses the daemon default.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun restart(
        id: String,
        signal: String? = null,
        t: Int? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/restart")) {
                signal?.let { parameter("signal", signal) }
                t?.let { parameter("t", t.toString()) }
            }.validateOnly()
        }

    /**
     * Kill a container
     *
     * Send a POSIX signal to a container, defaulting to killing to the container with `SIGKILL`.
     *
     * @param id Container ID or name.
     * @param signal Stop/kill signal; null leaves the daemon default.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun kill(
        id: String,
        signal: String? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/kill")) {
                signal?.let { parameter("signal", signal) }
            }.validateOnly()
        }

    /**
     * Updates supported resource limits and restart policy. Changing image, environment, ports or mounts
     * requires recreating the container; this method does not perform that workflow.
     *
     * @param id Container ID or name.
     * @param config Configuration sent to Docker as JSON.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun update(
        id: String,
        config: ContainerUpdateRequest
    ): Result<ContainerUpdateResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/update")) {
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Rename a container
     *
     * @param id Container ID or name.
     * @param name New container name, not an existing ID.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun rename(
        id: String,
        name: String
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/rename")) {
                parameter("name", name)
            }.validateOnly()
        }

    /**
     * Pause a container
     *
     * Use the freezer cgroup to suspend all processes in a container.
     *
     * Traditionally, when suspending a container the state of the container is preserved, for example, process
     * environment variables and memory contents.
     *
     * When the container is resumed, it will continue from where it left off.
     *
     * @param id Container ID or name.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun pause(id: String): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/pause"))
                .validateOnly()
        }

    /**
     * Unpause a container
     *
     * Resume a container which has been paused.
     *
     * @param id Container ID or name.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun unpause(id: String): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/unpause"))
                .validateOnly()
        }

    /**
     * Delete unused containers
     *
     * @param filters Docker filter names mapped to values, for example mapOf("status" to listOf("running"))
     * for listing, or mapOf("label" to listOf("app=worker")). Allowed keys depend on the operation;
     * the SDK encodes the map as JSON and Docker validates it.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<ContainerPruneResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/prune")) {
                filters?.let {
                    parameter(
                        "filters",
                        json.encodeToString(it)
                    )
                }
            }.parse()
        }

    /**
     * List processes running inside a container
     *
     * On Unix systems, this is done by running the `ps` command. This endpoint is not supported on Windows.
     *
     * @param id Container ID or name.
     * @param psArgs Arguments passed to the daemon-side ps command.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getTop(
        id: String,
        psArgs: String? = null
    ): Result<ContainerTopResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/containers/$id/top")) {
                psArgs?.let { parameter("ps_args", it) }
            }.parse()
        }

    /**
     * Get changes on a container's filesystem
     *
     * Returns which files in a container's filesystem have been added, deleted, or modified.
     * The `Kind` of modification can be one of:
     *
     * - `0`: Modified ("C")
     * - `1`: Added ("A")
     * - `2`: Deleted ("D")
     *
     * @param id Container ID or name.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getChanges(id: String): Result<List<FilesystemChange>, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/containers/$id/changes"))
                .parse()
        }

    /**
     * With stream=true, returns a cold flow whose HTTP request opens on collection.
     * HTTP errors during collection throw [dev.limebeck.libs.docker.client.model.DockerApiException]; malformed samples fail collection.
     * With stream=false, performs the request now and wraps a successful sample in a one-element flow.
     * Cancellation releases the streaming request. CPU percentages require successive counter samples.
     *
     * @param id Container ID or name.
     * @sample dev.limebeck.libs.docker.guide.oneStatsSample
     * @param stream Use a live cold flow when true; fetch a single response immediately when false.
     * @param oneShot Request one sample without waiting for a second CPU sample; used with stream=false.
     * @return Prepared flow or an error encountered before collection; see collection failure semantics above.
     */
    suspend fun getStats(
        id: String,
        stream: Boolean = true,
        oneShot: Boolean = false
    ): Result<Flow<ContainerStatsResponse>, ErrorResponse> =
        with(dockerClient) {
            if (!stream) {
                val response: Result<ContainerStatsResponse, ErrorResponse> =
                    client.get(apiPath("/containers/$id/stats")) {
                        parameter("stream", "false")
                        parameter("one-shot", oneShot.toString())
                    }.parse()
                return response.map { flow { emit(it) } }
            }

            val statsFlow = channelFlow {
                client.prepareGet(apiPath("/containers/$id/stats")) {
                    applyStreamConfig()
                    parameter("stream", "true")
                    parameter("one-shot", oneShot.toString())
                }.execute { response ->
                    response.consumeStream { channel ->
                        while (true) {
                            val line = channel.readDockerLine() ?: break
                            if (line.isBlank()) continue
                            val stats = json.decodeFromString<ContainerStatsResponse>(line)
                            send(stats)
                        }
                    }
                }
            }.buffer(0)
            return statsFlow.asSuccess()
        }

    /**
     * Changes the running container TTY dimensions. Use [Exec.resize] for an exec TTY.
     *
     * @param id Container ID or name.
     * @param h Terminal height in rows.
     * @param w Terminal width in columns.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun resize(
        id: String,
        h: Int,
        w: Int
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/resize")) {
                parameter("h", h.toString())
                parameter("w", w.toString())
            }.validateOnly()
        }

    /**
     * Wait for a container
     *
     * Block until a container stops, then returns the exit code.
     *
     * @param id Container ID or name.
     * @param condition Docker wait condition: not-running (default), next-exit, or removed.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun wait(
        id: String,
        condition: String? = null
    ): Result<ContainerWaitResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/wait")) {
                condition?.let { parameter("condition", it) }
            }.parse()
        }

    /**
     * Returns a tar archive of the container filesystem. Read the channel to completion or cancel it.
     * This is not a backup of data held in mounted volumes.
     *
     * @param id Container ID or name.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun export(id: String): Result<ByteReadChannel, ErrorResponse> =
        with(dockerClient) {
            val response = client.get(apiPath("/containers/$id/export"))
            return if (response.status.isSuccess()) {
                response.bodyAsChannel().asSuccess()
            } else {
                response.errorResult()
            }
        }

    /**
     * Returns the raw Base64-encoded X-Docker-Container-Path-Stat header, or an empty string when absent.
     * Decode the header separately to obtain path metadata. Bodyless HTTP errors remain error results.
     *
     * @param id Container ID or name.
     * @param path Path inside the container filesystem.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getArchiveInfo(
        id: String,
        path: String
    ): Result<String, ErrorResponse> =
        with(dockerClient) {
            val response = client.head(apiPath("/containers/$id/archive")) {
                parameter("path", path)
            }
            return if (response.status.isSuccess()) {
                (response.headers["X-Docker-Container-Path-Stat"] ?: "").asSuccess()
            } else {
                response.errorResult()
            }
        }

    /**
     * Returns a tar archive of the requested container path. Read the channel to completion or cancel it.
     *
     * @param id Container ID or name.
     * @param path Path inside the container filesystem.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getArchive(
        id: String,
        path: String
    ): Result<ByteReadChannel, ErrorResponse> =
        with(dockerClient) {
            val response =
                client.get(apiPath("/containers/$id/archive")) {
                    parameter("path", path)
                }
            return if (response.status.isSuccess()) {
                response.bodyAsChannel().asSuccess()
            } else {
                response.errorResult()
            }
        }

    /**
     * Extract an archive of files or folders into a directory in a container
     *
     * Upload a tar archive to be extracted to a path in the filesystem of container id.
     *
     * @param id Container ID or name.
     * @param path Path inside the container filesystem.
     * @param body Tar archive channel consumed by this operation; retries require a fresh source.
     * @param noOverwriteDirNonDir Prevent replacing a directory with a file or vice versa.
     * @param copyUIDGID Preserve user and group ownership from the archive.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun putArchive(
        id: String,
        path: String,
        body: ByteReadChannel,
        noOverwriteDirNonDir: Boolean? = null,
        copyUIDGID: Boolean? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.put(apiPath("/containers/$id/archive")) {
                parameter("path", path)
                noOverwriteDirNonDir?.let { parameter("noOverwriteDirNonDir", it.toString()) }
                copyUIDGID?.let { parameter("copyUIDGID", it.toString()) }
                setBody(body)
            }.validateOnly()
        }

    /**
     * Creates an exec instance in a running container without starting its command.
     * Pass the returned ID to [Exec.startInteractive] or [Exec.startAndForget].
     *
     * @param id Container ID or name.
     * @param config Command arguments, working directory, user and stdin/stdout/stderr attachment flags.
     * For an interactive shell, enable stdin/stdout/stderr attachment and use the same tty value at start.
     * @sample dev.limebeck.libs.docker.guide.openShell
     * @return Exec instance ID for [Exec.startInteractive] or [Exec.startAndForget]; creating it does not run the command.
     */
    suspend fun execCreate(
        id: String,
        config: ExecConfig
    ): Result<IDResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/containers/$id/exec")) {
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Attaches to the existing container process; it does not start a new shell.
     * The caller owns the returned [ExecSession]. Collect its output once and close it if never collected.
     * Cancellation/completion of output closes the raw connection. TTY framing follows the inspected container configuration.
     *
     * @param id Container ID or name.
     * @param detachKeys Docker detach key sequence; null uses the daemon default.
     * @param logs Include existing output before any live output.
     * @param stream Continue receiving live output.
     * @param stdin Attach standard input.
     * @param stdout Include standard output.
     * @param stderr Include standard error.
     * @return Owned interactive session, or the Docker error response.
     */
    suspend fun attach(
        id: String,
        detachKeys: String? = null,
        logs: Boolean = false,
        stream: Boolean = false,
        stdin: Boolean = false,
        stdout: Boolean = false,
        stderr: Boolean = false
    ): Result<ExecSession, ErrorResponse> =
        with(dockerClient) {
            coroutineScope {
                val inspection = getInfo(id)
                inspection.errorResultOrNull()?.let { return@coroutineScope it }
                val container = inspection.getOrThrow()

                val isTty = container.config?.tty == true

                return@coroutineScope createInteractiveSession(
                    tty = isTty,
                    method = HttpMethod.Post,
                    path = "/containers/$id/attach",
                    parameters = parameters {
                        detachKeys?.let { append("detachKeys", it) }
                        append("logs", logs.toString())
                        append("stream", stream.toString())
                        append("stdin", stdin.toString())
                        append("stdout", stdout.toString())
                        append("stderr", stderr.toString())
                    },
                    headers = mapOf(
                        "Host" to "docker",
                        "Connection" to "Upgrade",
                        "Upgrade" to "tcp",
                    )
                )
            }
        }
}
