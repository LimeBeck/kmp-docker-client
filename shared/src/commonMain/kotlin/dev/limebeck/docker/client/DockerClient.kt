package dev.limebeck.docker.client

import dev.limebeck.docker.client.dslUtils.ApiCacheHolder
import dev.limebeck.docker.client.model.ErrorResponse
import dev.limebeck.docker.client.model.Result
import dev.limebeck.docker.client.model.asError
import dev.limebeck.docker.client.model.asSuccess
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

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
}

