package dev.limebeck.docker.client.api

import dev.limebeck.docker.client.DockerClient
import dev.limebeck.docker.client.dslUtils.ApiCacheHolder
import dev.limebeck.docker.client.model.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private object ContainersKey

val DockerClient.containers: DockerContainersApi
    get() = (this as ApiCacheHolder).apiCache.getOrPut(ContainersKey) {
        DockerContainersApi(this)
    } as DockerContainersApi

class DockerContainersApi(val dockerClient: DockerClient) {
    suspend fun getList(): Result<List<ContainerSummary>, ErrorResponse> = with(dockerClient) {
        return client.get("/containers/json").parse()
    }

    suspend fun getInfo(id: String): Result<ContainerInspectResponse, ErrorResponse> = with(dockerClient) {
        return client.get("/containers/$id/json").parse()
    }

    suspend fun getLogs(
        id: String,
        parameters: ContainerLogsParameters = ContainerLogsParameters()
    ): Result<Flow<LogLine>, ErrorResponse> = with(dockerClient) {
        coroutineScope {
            val container = getInfo(id).onError {
                return@coroutineScope Result.error(it)
            }.getOrNull() ?: return@coroutineScope Result.error(ErrorResponse("Container not found"))

            val logs = flow {
                client.prepareGet("/containers/${id}/logs") {
                    applyConnectionConfig()

                    parameter("follow", parameters.follow.toString())
                    parameter("timestamps", parameters.timestamps.toString())
                    parameter("stdout", parameters.stdout.toString())
                    parameter("stderr", parameters.stderr.toString())

                    parameters.until?.let { parameter("until", it) }
                    parameters.since?.let { parameter("since", it) }
                    parameters.tail?.let { parameter("tail", it) }

                    timeout {
                        requestTimeoutMillis = 100_000
                    }
                }.execute {
                    val channel = it.bodyAsChannel()
                    emitAll(channel.readLogLines(container.config?.tty == true))
                }
            }

            Result.success(logs)
        }
    }

    suspend fun create(
        name: String? = null,
        config: ContainerConfig = ContainerConfig()
    ): Result<ContainerCreateResponse, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/create") {
            name?.let { parameter("name", it) }
            contentType(ContentType.Application.Json)
            setBody(config)
        }.parse()
    }

    suspend fun start(id: String): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/start").validateOnly()
    }

    suspend fun stop(
        id: String,
        signal: String? = null,
        t: Int? = null
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/stop") {
            signal?.let { parameter("signal", signal) }
            t?.let { parameter("t", t.toString()) }
        }.validateOnly()
    }

    suspend fun remove(
        id: String,
        force: Boolean = false,
        link: Boolean = false,
        v: Boolean = false
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.delete("/containers/$id") {
            parameter("force", force.toString())
            parameter("link", link.toString())
            parameter("v", v.toString())
        }.validateOnly()
    }

    suspend fun restart(
        id: String,
        signal: String? = null,
        t: Int? = null
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/restart") {
            signal?.let { parameter("signal", signal) }
            t?.let { parameter("t", t.toString()) }
        }.validateOnly()
    }

    suspend fun kill(
        id: String,
        signal: String? = null
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/kill") {
            signal?.let { parameter("signal", signal) }
        }.validateOnly()
    }

    suspend fun update(
        id: String,
        config: ContainerUpdateRequest
    ): Result<ContainerUpdateResponse, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/update") {
            contentType(ContentType.Application.Json)
            setBody(config)
        }.parse()
    }

    suspend fun rename(
        id: String,
        name: String
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/rename") {
            parameter("name", name)
        }.validateOnly()
    }

    suspend fun pause(id: String): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/pause").validateOnly()
    }

    suspend fun unpause(id: String): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/unpause").validateOnly()
    }

    suspend fun prune(
        filters: Map<String, List<String>>? = null
    ): Result<ContainerPruneResponse, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/prune") {
            filters?.let { parameter("filters", json.encodeToString(it)) }
        }.parse()
    }

    suspend fun getTop(
        id: String,
        psArgs: String? = null
    ): Result<ContainerTopResponse, ErrorResponse> = with(dockerClient) {
        return client.get("/containers/$id/top") {
            psArgs?.let { parameter("ps_args", it) }
        }.parse()
    }

    suspend fun getChanges(id: String): Result<List<FilesystemChange>, ErrorResponse> = with(dockerClient) {
        return client.get("/containers/$id/changes").parse()
    }

    suspend fun getStats(
        id: String,
        stream: Boolean = true,
        oneShot: Boolean = false
    ): Result<Flow<ContainerStatsResponse>, ErrorResponse> = with(dockerClient) {
        if (!stream) {
            val response: Result<ContainerStatsResponse, ErrorResponse> = client.get("/containers/$id/stats") {
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
                    val stats = json.decodeFromString<ContainerStatsResponse>(line)
                    emit(stats)
                }
            }
        }
        return statsFlow.asSuccess()
    }

    suspend fun resize(
        id: String,
        h: Int,
        w: Int
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/resize") {
            parameter("h", h.toString())
            parameter("w", w.toString())
        }.validateOnly()
    }

    suspend fun wait(
        id: String,
        condition: String? = null
    ): Result<ContainerWaitResponse, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/wait") {
            condition?.let { parameter("condition", it) }
        }.parse()
    }

    suspend fun export(id: String): Result<ByteReadChannel, ErrorResponse> = with(dockerClient) {
        val response = client.get("/containers/$id/export")
        return if (response.status.isSuccess()) {
            response.bodyAsChannel().asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(response.bodyAsText()).asError()
        }
    }

    suspend fun getArchiveInfo(
        id: String,
        path: String
    ): Result<String, ErrorResponse> = with(dockerClient) {
        val response = client.head("/containers/$id/archive") {
            parameter("path", path)
        }
        return if (response.status.isSuccess()) {
            (response.headers["X-Docker-Container-Path-Stat"] ?: "").asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(response.bodyAsText()).asError()
        }
    }

    suspend fun getArchive(
        id: String,
        path: String
    ): Result<ByteReadChannel, ErrorResponse> = with(dockerClient) {
        val response = client.get("/containers/$id/archive") {
            parameter("path", path)
        }
        return if (response.status.isSuccess()) {
            response.bodyAsChannel().asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(response.bodyAsText()).asError()
        }
    }

    suspend fun putArchive(
        id: String,
        path: String,
        body: ByteReadChannel,
        noOverwriteDirNonDir: Boolean? = null,
        copyUIDGID: Boolean? = null
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.put("/containers/$id/archive") {
            parameter("path", path)
            noOverwriteDirNonDir?.let { parameter("noOverwriteDirNonDir", it.toString()) }
            copyUIDGID?.let { parameter("copyUIDGID", it.toString()) }
            setBody(body)
        }.validateOnly()
    }

    suspend fun execCreate(
        id: String,
        config: ExecConfig
    ): Result<IDResponse, ErrorResponse> = with(dockerClient) {
        return client.post("/containers/$id/exec") {
            contentType(ContentType.Application.Json)
            setBody(config)
        }.parse()
    }

    suspend fun attach(
        id: String,
        detachKeys: String? = null,
        logs: Boolean = false,
        stream: Boolean = false,
        stdin: Boolean = false,
        stdout: Boolean = false,
        stderr: Boolean = false
    ): Result<Flow<LogLine>, ErrorResponse> = with(dockerClient) {
        coroutineScope {
            val container = getInfo(id).onError {
                return@coroutineScope Result.error(it)
            }.getOrNull() ?: return@coroutineScope Result.error(ErrorResponse("Container not found"))

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
                    emitAll(channel.readLogLines(container.config?.tty == true))
                }
            }
            Result.success(attachFlow)
        }
    }
}