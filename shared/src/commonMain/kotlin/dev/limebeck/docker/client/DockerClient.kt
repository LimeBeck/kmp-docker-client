package dev.limebeck.docker.client

import dev.limebeck.docker.client.model.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DockerClient(
    config: DockerClientConfig = DockerClientConfig()
) {
    companion object {
        const val API_VERSION = "1.51"
    }

    private val json = config.json

    private val client = HttpClient(CIO) {
        install(SSE)
        defaultRequest {
            url("http://localhost/$API_VERSION")
            unixSocket("/var/run/docker.sock")
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val scope = config.coroutineScope

    suspend fun getContainersList(): Result<List<ContainerSummary>, ErrorResponse> {
        return client.get("/containers/json").parse()
    }

    suspend fun getContainerInfo(id: String): Result<ContainerInspectResponse, ErrorResponse> {
        return client.get("/containers/$id/json").parse()
    }

    suspend fun getContainerLogs(
        id: String,
        parameters: ContainerLogsParameters = ContainerLogsParameters()
    ): Result<Flow<LogLine>, ErrorResponse> = withContext(scope.coroutineContext) {
        val container = getContainerInfo(id).onError {
            return@withContext it.asError()
        }.getOrNull() ?: return@withContext ErrorResponse("Container not found").asError()

        val logs = MutableSharedFlow<LogLine>()

        scope.launch {
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

                    logs.emit(message)
                }
                println("End stream")
                currentCoroutineContext().cancel()
            }
        }

        return@withContext logs.asSuccess()
    }

    suspend fun createContainer(
        name: String? = null,
        config: ContainerConfig = ContainerConfig()
    ): Result<ContainerCreateResponse, ErrorResponse> {
        return client.post("/containers/create") {
            contentType(ContentType.Application.Json)
            setBody(config)
        }.parse()
    }

    suspend fun startContainer(id: String): Result<Unit, ErrorResponse> {
        return client.post("/containers/$id/start").validateOnly()
    }

    private suspend inline fun <reified T> HttpResponse.parse(): Result<T, ErrorResponse> {
        return if (status.isSuccess()) {
            json.decodeFromString<T>(bodyAsText()).asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(bodyAsText()).asError()
        }
    }

    private suspend inline fun HttpResponse.validateOnly(): Result<Unit, ErrorResponse> {
        return if (status.isSuccess()) {
            Unit.asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(bodyAsText()).asError()
        }
    }
}


