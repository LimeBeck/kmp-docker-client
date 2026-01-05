package dev.limebeck.docker.client

import dev.limebeck.docker.client.dslUtils.ApiCacheHolder
import dev.limebeck.docker.client.model.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

open class DockerClient(
    val config: DockerClientConfig = DockerClientConfig()
) : ApiCacheHolder {
    companion object {
        const val API_VERSION = "1.51"
    }

    val json = config.json

    override val apiCache: MutableMap<Any, Any> = mutableMapOf()

    val client = HttpClient(CIO) {
        install(SSE)
        defaultRequest {
            url("http://${config.hostname}/$API_VERSION")
            unixSocket("/var/run/docker.sock")
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend inline fun <reified T> HttpResponse.parse(): Result<T, ErrorResponse> {
        return if (status.isSuccess()) {
            json.decodeFromString<T>(bodyAsText()).asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(bodyAsText()).asError()
        }
    }

    suspend inline fun HttpResponse.validateOnly(): Result<Unit, ErrorResponse> {
        return if (status.isSuccess()) {
            Unit.asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(bodyAsText()).asError()
        }
    }

    fun HttpRequestBuilder.applyConnectionConfig() {
        url("http://${config.hostname}/$API_VERSION")
        unixSocket("/var/run/docker.sock")
    }

    fun ByteReadChannel.readLogLines(isTty: Boolean): Flow<LogLine> = flow {
        while (!isClosedForRead) {
            val message = if (!isTty) {
                val header = ByteArray(8)
                try {
                    readFully(header)
                } catch (e: Exception) {
                    break
                }

                val streamType = header[0].toInt()
                val payloadSize = (
                        ((header[4].toInt() and 0xFF) shl 24) or
                                ((header[5].toInt() and 0xFF) shl 16) or
                                ((header[6].toInt() and 0xFF) shl 8) or
                                (header[7].toInt() and 0xFF)
                        )

                if (payloadSize < 0) break

                val payloadBuffer = ByteArray(payloadSize)
                readFully(payloadBuffer)

                LogLine(
                    line = payloadBuffer.decodeToString(),
                    type = when (streamType) {
                        0 -> LogLine.Type.STDOUT // stdin is written on stdout
                        1 -> LogLine.Type.STDOUT
                        2 -> LogLine.Type.STDERR
                        else -> LogLine.Type.UNKNOWN
                    }
                )
            } else {
                LogLine(
                    line = readUTF8Line() ?: "",
                    type = LogLine.Type.UNKNOWN
                )
            }

            emit(message)
        }
    }
}
