package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.DockerClientConfig.Auth
import dev.limebeck.libs.docker.client.api.AUTH_HEADER
import dev.limebeck.libs.docker.client.api.resolveServerForRegistry
import dev.limebeck.libs.docker.client.dsl.ApiCacheHolder
import dev.limebeck.libs.docker.client.model.ErrorResponse
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
import kotlinx.serialization.SerializationException
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
