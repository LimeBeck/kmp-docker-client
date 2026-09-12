package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.utils.readDockerLine

import dev.limebeck.libs.docker.client.DockerClientConfig.Auth
import dev.limebeck.libs.docker.client.api.AUTH_HEADER
import dev.limebeck.libs.docker.client.api.resolveServerForRegistry
import dev.limebeck.libs.docker.client.dsl.ApiCacheHolder
import dev.limebeck.libs.docker.client.model.ImageProgress
import dev.limebeck.libs.docker.client.model.ErrorResponse
import dev.limebeck.libs.docker.client.model.DockerApiException
import dev.limebeck.libs.docker.client.model.Result
import dev.limebeck.libs.docker.client.model.asError
import dev.limebeck.libs.docker.client.model.asSuccess
import dev.limebeck.libs.docker.client.socket.DockerRawConnection
import dev.limebeck.libs.docker.client.socket.openRawConnectionUnix
import dev.limebeck.libs.logger.logger
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64

open class DockerClient(
    val config: DockerClientConfig = DockerClientConfig()
) : ApiCacheHolder {
    companion object {
        const val API_VERSION = "1.51"
        val logger = DockerClient::class.logger()
    }

    val json = config.json

    override val apiCache: MutableMap<Any, Any> = mutableMapOf()

    val client = HttpClient(CIO) {
        install(SSE)
        install(HttpTimeout)
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    DockerClient.logger.debug { message }
                }
            }
            level = LogLevel.HEADERS
            sanitizeHeader { header ->
                header.equals(AUTH_HEADER, ignoreCase = true) ||
                    header.equals("X-Registry-Config", ignoreCase = true) ||
                    header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                    header.equals(HttpHeaders.ProxyAuthorization, ignoreCase = true)
            }
            filter { request -> !request.url.encodedPath.endsWith("/auth") }
        }
        defaultRequest {
            when (config.connectionConfig) {
                is DockerClientConfig.ConnectionConfig.SocketConnection -> {
                    url("http://localhost")
                    unixSocket(config.connectionConfig.socketPath)
                }
            }
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** Builds the path shared by HTTP and raw hijack requests. */
    fun apiPath(path: String): String {
        val absolutePath = "/${path.trimStart('/')}"
        val prefix = "/v$API_VERSION"
        return if (absolutePath == prefix || absolutePath.startsWith("$prefix/")) {
            absolutePath
        } else {
            "$prefix$absolutePath"
        }
    }

    suspend inline fun <reified T> HttpResponse.parse(): Result<T, ErrorResponse> {
        return if (status.isSuccess()) {
            json.decodeFromString<T>(bodyAsText()).asSuccess()
        } else {
            errorResponse().asError()
        }
    }

    suspend inline fun HttpResponse.validateOnly(): Result<Unit, ErrorResponse> {
        return if (status.isSuccess()) {
            Unit.asSuccess()
        } else {
            errorResponse().asError()
        }
    }

    /** Handles bodyless HEAD errors and non-JSON daemon/proxy responses. */
    suspend fun HttpResponse.errorResponse(): ErrorResponse {
        val fallback = ErrorResponse("Docker API returned HTTP ${status.value} ${status.description}")
        if (request.method == HttpMethod.Head) return fallback
        val text = bodyAsText()
        if (text.isBlank()) return fallback
        return try {
            json.decodeFromString<ErrorResponse>(text)
        } catch (_: SerializationException) {
            fallback
        }
    }

    /** Cold streams surface HTTP failures during collection, before decoding data. */
    suspend fun HttpResponse.requireStreamSuccess() {
        if (!status.isSuccess()) throw DockerApiException(status, errorResponse())
    }

    /** Validate transport completion as well as status; CIO can report a short body as clean EOF. */
    suspend fun <T> HttpResponse.consumeStream(block: suspend (ByteReadChannel) -> T): T {
        requireStreamSuccess()
        val channel = bodyAsChannel().counted()
        val result = block(channel)
        channel.closedCause?.let { throw it }
        val expected = headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (headers[HttpHeaders.TransferEncoding] == null && expected != null && channel.totalBytesRead != expected) {
            throw kotlinx.io.EOFException("Truncated Docker response: expected $expected bytes, received ${channel.totalBytesRead}")
        }
        return result
    }

    /** Docker can report an operation failure inside a successful NDJSON response. */
    suspend fun HttpResponse.validateImageProgress(): Result<Unit, ErrorResponse> = validateImageProgress {}

    suspend fun HttpResponse.validateImageProgress(
        onProgress: suspend (ImageProgress) -> Unit,
    ): Result<Unit, ErrorResponse> {
        if (!status.isSuccess()) return errorResponse().asError()
        val channel = bodyAsChannel().counted()
        while (true) {
            val line = try {
                channel.readDockerLine()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return ErrorResponse(error.message ?: "Failed to read Docker image progress").asError()
            } ?: break
            if (line.isBlank()) continue
            val message = try {
                json.parseToJsonElement(line) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return ErrorResponse("Invalid Docker image progress response").asError()
            val detail = (message["errorDetail"] as? JsonObject)?.get("message") as? JsonPrimitive
            val legacyError = message["error"] as? JsonPrimitive
            val error = detail?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: legacyError?.contentOrNull?.takeIf { it.isNotBlank() }
            if (error != null) return ErrorResponse(error).asError()
            // Deliberately outside parsing/read catches: consumer failures belong to the caller.
            onProgress(ImageProgress(message))
        }
        channel.closedCause?.let { error ->
            if (error is CancellationException) throw error
            return ErrorResponse(error.message ?: "Failed to read Docker image progress").asError()
        }
        val expected = headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (headers[HttpHeaders.TransferEncoding] == null && expected != null && channel.totalBytesRead != expected) {
            return ErrorResponse("Truncated Docker image progress response").asError()
        }
        return Unit.asSuccess()
    }

    /** Live streams may be idle indefinitely; the collector owns their lifetime. */
    fun HttpRequestBuilder.applyStreamConfig() {
        applyConnectionConfig()
        timeout {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }

    @OptIn(InternalAPI::class)
    fun HttpRequestBuilder.applyConnectionConfig() {
        when (config.connectionConfig) {
            is DockerClientConfig.ConnectionConfig.SocketConnection -> {
                setCapability(UnixSocketCapability, UnixSocketSettings(config.connectionConfig.socketPath))
            }
        }
    }

    fun HttpRequestBuilder.applyAuthForRegistry(registry: String) {
        val (serverAddress, auth) = resolveServerForRegistry(registry)
            .map { it to config.auth[it] }
            .firstOrNull { it.second != null }
            ?: return

        val authHeader = when (auth!!) {
            is Auth.Credentials -> {
                mapOf(
                    "username" to auth.username,
                    "password" to auth.password,
                    "serveraddress" to serverAddress
                )
            }

            is Auth.Token -> {
                mapOf("identitytoken" to auth.token)
            }
        }
        header(
            AUTH_HEADER,
            Base64.encode(json.encodeToString(authHeader).toByteArray())
        )
    }

    suspend fun openRawConnection(): DockerRawConnection = when (config.connectionConfig) {
        is DockerClientConfig.ConnectionConfig.SocketConnection -> {
            logger.debug { "Open raw socket connection" }
            openRawConnectionUnix(config.connectionConfig.socketPath)
        }
    }
}
