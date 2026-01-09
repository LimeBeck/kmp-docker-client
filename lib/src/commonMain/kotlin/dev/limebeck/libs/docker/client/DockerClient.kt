package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.DockerClientConfig.Auth
import dev.limebeck.libs.docker.client.api.AUTH_HEADER
import dev.limebeck.libs.docker.client.api.resolveServerForRegistry
import dev.limebeck.libs.docker.client.dslUtils.ApiCacheHolder
import dev.limebeck.libs.docker.client.model.ErrorResponse
import dev.limebeck.libs.docker.client.model.Result
import dev.limebeck.libs.docker.client.model.asError
import dev.limebeck.libs.docker.client.model.asSuccess
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
import io.ktor.utils.io.core.*
import kotlin.io.encoding.Base64

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
            when (config.connectionConfig) {
                is DockerClientConfig.ConnectionConfig.SocketConnection -> {
                    url("http://localhost/${API_VERSION}")
                    unixSocket(config.connectionConfig.socketPath)
                }
            }
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend inline fun <reified T> HttpResponse.parse(): Result<T, ErrorResponse> {
        val text = bodyAsText()
        return if (status.isSuccess()) {
            json.decodeFromString<T>(text).asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(text).asError()
        }
    }

    suspend inline fun HttpResponse.validateOnly(): Result<Unit, ErrorResponse> {
        return if (status.isSuccess()) {
            Unit.asSuccess()
        } else {
            json.decodeFromString<ErrorResponse>(bodyAsText()).asError()
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
}
