package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.utils.readLogLines
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

val DockerClient.containers by ::Containers.api()

class Containers(private val dockerClient: DockerClient) {
    /**
     * List containers
     *
     * Returns a list of containers. For details on the format, see the [inspect endpoint](#operation/ContainerInspect).
     * Note that it uses a different, smaller representation of a container than inspecting a single container.
     * For example, the list of linked containers is not propagated.
     */
    suspend fun getList(
        all: Boolean = false,
        limit: Int? = null,
        size: Boolean = false,
        filters: Map<String, List<String>>? = null,
    ): Result<List<ContainerSummary>, ErrorResponse> =
        with(dockerClient) {
            return client.get("/containers/json") {
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
     */
    suspend fun getInfo(id: String): Result<ContainerInspectResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get("/containers/$id/json").parse()
        }

    /**
     * Get container logs
     *
     * Get `stdout` and `stderr` logs from a container.
     * Note: This endpoint works only for containers with the `json-file` or `journald` logging driver.
     */
    suspend fun getLogs(
        id: String,
        parameters: ContainerLogsParameters = ContainerLogsParameters()
    ): Result<Flow<LogLine>, ErrorResponse> =
        with(dockerClient) {
            coroutineScope {
                val container = getInfo(id).onError {
                    return@coroutineScope it.asError()
                }.getOrNull()
                    ?: return@coroutineScope ErrorResponse("Container not found").asError()

                val logs = flow {
                    client.prepareGet("/containers/${id}/logs") {

                        parameter("follow", parameters.follow.toString())
                        parameter("timestamps", parameters.timestamps.toString())
                        parameter("stdout", parameters.stdout.toString())
                        parameter("stderr", parameters.stderr.toString())

                        parameters.until?.let { parameter("until", it) }
                        parameters.since?.let { parameter("since", it) }
                        parameters.tail?.let { parameter("tail", it) }

                        applyConnectionConfig()
                        timeout {
                            requestTimeoutMillis = 100_000
                        }
                    }.execute {
                        val channel = it.bodyAsChannel()
                        channel.readLogLines(container.config?.tty == true, this@flow)
                    }
                }

                return@coroutineScope logs.asSuccess()
            }
        }

    /**
     * Create a container
     */
    suspend fun create(
        name: String? = null,
        config: ContainerConfig = ContainerConfig()
    ): Result<ContainerCreateResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/create") {
                name?.let { parameter("name", it) }
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Start a container
     */
    suspend fun start(id: String): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/start")
                .validateOnly()
        }

    /**
     * Stop a container
     */
    suspend fun stop(
        id: String,
        signal: String? = null,
        t: Int? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/stop") {
                signal?.let { parameter("signal", signal) }
                t?.let { parameter("t", t.toString()) }
            }.validateOnly()
        }

    /**
     * Remove a container
     */
    suspend fun remove(
        id: String,
        force: Boolean = false,
        link: Boolean = false,
        v: Boolean = false
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.delete("/containers/$id") {
                parameter("force", force.toString())
                parameter("link", link.toString())
                parameter("v", v.toString())
            }.validateOnly()
        }

    /**
     * Restart a container
     */
    suspend fun restart(
        id: String,
        signal: String? = null,
        t: Int? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/restart") {
                signal?.let { parameter("signal", signal) }
                t?.let { parameter("t", t.toString()) }
            }.validateOnly()
        }

    /**
     * Kill a container
     *
     * Send a POSIX signal to a container, defaulting to killing to the container with `SIGKILL`.
     */
    suspend fun kill(
        id: String,
        signal: String? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/kill") {
                signal?.let { parameter("signal", signal) }
            }.validateOnly()
        }

    /**
     * Update a container
     *
     * Change various configuration options of a container without having to recreate it.
     */
    suspend fun update(
        id: String,
        config: ContainerUpdateRequest
    ): Result<ContainerUpdateResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/update") {
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Rename a container
     */
    suspend fun rename(
        id: String,
        name: String
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/rename") {
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
     */
    suspend fun pause(id: String): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/pause")
                .validateOnly()
        }

    /**
     * Unpause a container
     *
     * Resume a container which has been paused.
     */
    suspend fun unpause(id: String): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/unpause")
                .validateOnly()
        }

    /**
     * Delete unused containers
     */
    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<ContainerPruneResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/prune") {
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
     */
    suspend fun getTop(
        id: String,
        psArgs: String? = null
    ): Result<ContainerTopResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get("/containers/$id/top") {
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
     */
    suspend fun getChanges(id: String): Result<List<FilesystemChange>, ErrorResponse> =
        with(dockerClient) {
            return client.get("/containers/$id/changes")
                .parse()
        }

    /**
     * Get container stats based on resource usage
     *
     * This endpoint returns a live stream of a container’s resource usage statistics.
     *
     * The `precpu_stats` is the CPU statistic of the previous read, and is used to calculate the CPU usage percentage.
     * It is not applicable to the first read.
     *
     * On Empire, the `networks` object is only present if the container is using the `bridge` network driver.
     */
    suspend fun getStats(
        id: String,
        stream: Boolean = true,
        oneShot: Boolean = false
    ): Result<Flow<ContainerStatsResponse>, ErrorResponse> =
        with(dockerClient) {
            if (!stream) {
                val response: Result<ContainerStatsResponse, ErrorResponse> =
                    client.get("/containers/$id/stats") {
                        parameter("stream", "false")
                        parameter("one-shot", oneShot.toString())
                    }.parse()
                return response.map { flow { emit(it) } }
            }

            val statsFlow = flow {
                client.prepareGet("/containers/$id/stats") {
                    applyConnectionConfig()
                    parameter("stream", "true")
                    parameter("one-shot", oneShot.toString())
                }.execute { response ->
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.isEmpty()) continue
                        val stats =
                            json.decodeFromString<ContainerStatsResponse>(
                                line
                            )
                        emit(stats)
                    }
                }
            }
            return statsFlow.asSuccess()
        }

    /**
     * Resize a container TTY
     *
     * Resize the TTY session used by a container. You must restart the container for the resize to take effect.
     */
    suspend fun resize(
        id: String,
        h: Int,
        w: Int
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/resize") {
                parameter("h", h.toString())
                parameter("w", w.toString())
            }.validateOnly()
        }

    /**
     * Wait for a container
     *
     * Block until a container stops, then returns the exit code.
     */
    suspend fun wait(
        id: String,
        condition: String? = null
    ): Result<ContainerWaitResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/wait") {
                condition?.let { parameter("condition", it) }
            }.parse()
        }

    /**
     * Export a container
     *
     * Export the contents of a container as a tarball.
     */
    suspend fun export(id: String): Result<ByteReadChannel, ErrorResponse> =
        with(dockerClient) {
            val response = client.get("/containers/$id/export")
            return if (response.status.isSuccess()) {
                response.bodyAsChannel().asSuccess()
            } else {
                json.decodeFromString<ErrorResponse>(
                    response.bodyAsText()
                ).asError()
            }
        }

    /**
     * Get information about files in a container
     *
     * A response header `X-Docker-Container-Path-Stat` is return containing a base64 - encoded JSON object with some filesystem statistics.
     */
    suspend fun getArchiveInfo(
        id: String,
        path: String
    ): Result<String, ErrorResponse> =
        with(dockerClient) {
            val response = client.head("/containers/$id/archive") {
                parameter("path", path)
            }
            return if (response.status.isSuccess()) {
                (response.headers["X-Docker-Container-Path-Stat"] ?: "").asSuccess()
            } else {
                json.decodeFromString<ErrorResponse>(
                    response.bodyAsText()
                ).asError()
            }
        }

    /**
     * Get an archive of a filesystem resource in a container
     *
     * Get a tar archive of a resource in the filesystem of container id.
     */
    suspend fun getArchive(
        id: String,
        path: String
    ): Result<ByteReadChannel, ErrorResponse> =
        with(dockerClient) {
            val response =
                client.get("/containers/$id/archive") {
                    parameter("path", path)
                }
            return if (response.status.isSuccess()) {
                response.bodyAsChannel().asSuccess()
            } else {
                json.decodeFromString<ErrorResponse>(
                    response.bodyAsText()
                ).asError()
            }
        }

    /**
     * Extract an archive of files or folders into a directory in a container
     *
     * Upload a tar archive to be extracted to a path in the filesystem of container id.
     */
    suspend fun putArchive(
        id: String,
        path: String,
        body: ByteReadChannel,
        noOverwriteDirNonDir: Boolean? = null,
        copyUIDGID: Boolean? = null
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.put("/containers/$id/archive") {
                parameter("path", path)
                noOverwriteDirNonDir?.let { parameter("noOverwriteDirNonDir", it.toString()) }
                copyUIDGID?.let { parameter("copyUIDGID", it.toString()) }
                setBody(body)
            }.validateOnly()
        }

    /**
     * Create an exec instance
     *
     * Run a command inside a running container.
     */
    suspend fun execCreate(
        id: String,
        config: ExecConfig
    ): Result<IDResponse, ErrorResponse> =
        with(dockerClient) {
            return client.post("/containers/$id/exec") {
                contentType(ContentType.Application.Json)
                setBody(config)
            }.parse()
        }

    /**
     * Attach to a container
     *
     * Attach to a container to freely input into it and receive output, including it's initial output.
     */
    suspend fun attach(
        id: String,
        detachKeys: String? = null,
        logs: Boolean = false,
        stream: Boolean = false,
        stdin: Boolean = false,
        stdout: Boolean = false,
        stderr: Boolean = false
    ): Result<Flow<LogLine>, ErrorResponse> =
        with(dockerClient) {
            coroutineScope {
                val container = getInfo(id).onError {
                    return@coroutineScope Result.error(
                        it
                    )
                }.getOrNull()
                    ?: return@coroutineScope Result.error(
                        ErrorResponse("Container not found")
                    )

                val attachFlow = flow {
                    client.preparePost("/containers/$id/attach") {
                        applyConnectionConfig()
                        detachKeys?.let { parameter("detachKeys", it) }
                        parameter("logs", logs.toString())
                        parameter("stream", stream.toString())
                        parameter("stdin", stdin.toString())
                        parameter("stdout", stdout.toString())
                        parameter("stderr", stderr.toString())
                    }.execute { response ->
                        val channel = response.bodyAsChannel()
                        channel.readLogLines(container.config?.tty == true, this@flow)
                    }
                }
                attachFlow.asSuccess()
            }
        }
}