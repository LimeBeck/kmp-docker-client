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
                return@coroutineScope it.asError()
            }.getOrNull() ?: return@coroutineScope ErrorResponse("Container not found").asError()

            val logs = flow {
                client.prepareGet("/containers/${id}/logs") {
                    parameter("follow", parameters.follow.toString())
                    parameter("timestamps", parameters.timestamps.toString())
                    parameter("stdout", parameters.stdout.toString())
                    parameter("stderr", parameters.stderr.toString())

                    parameters.until?.let { parameter("until", it) }
                    parameters.since?.let { parameter("since", it) }
                    parameters.tail?.let { parameter("tail", it) }

                    unixSocket("/var/run/docker.sock")
                    timeout {
                        requestTimeoutMillis = 100_000
                    }
                }.execute {
                    val channel = it.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val message = if (container.config?.tty != true) {
                            val header = ByteArray(8)
                            try {
                                // Пытаемся прочитать ровно 8 байт
                                channel.readFully(header)
                            } catch (e: Exception) {
                                // Если поток закрылся или данных меньше 8 байт (EOF)
                                break
                            }

                            // 2. Определяем тип потока (первый байт)
                            val streamType = header[0].toInt()

                            // 3. Определяем размер payload (последние 4 байта, Big Endian)
                            val payloadSize = (
                                    ((header[4].toInt() and 0xFF) shl 24) or
                                            ((header[5].toInt() and 0xFF) shl 16) or
                                            ((header[6].toInt() and 0xFF) shl 8) or
                                            (header[7].toInt() and 0xFF)
                                    )

                            // Проверка на адекватность размера (на всякий случай)
                            if (payloadSize < 0) break

                            // 4. Читаем само сообщение ровно указанной длины
                            val payloadBuffer = ByteArray(payloadSize)
                            channel.readFully(payloadBuffer)

                            LogLine(
                                line = payloadBuffer.decodeToString(),
                                type = when (streamType) {
                                    1 -> LogLine.Type.STDOUT
                                    2 -> LogLine.Type.STDERR
                                    else -> LogLine.Type.UNKNOWN
                                }
                            )
                        } else {
                            LogLine(
                                line = channel.readUTF8Line() ?: "",
                                type = LogLine.Type.UNKNOWN
                            )
                        }

                        emit(message)
                    }
                }
            }

            return@coroutineScope logs.asSuccess()
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


}