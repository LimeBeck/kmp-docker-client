package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dslUtils.api
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

val DockerClient.system by ::System.api()

class System(private val dockerClient: DockerClient) {
    suspend fun getInfo(): Result<SystemInfo, ErrorResponse> =
        with(dockerClient) {
            return client.get("/info").parse()
        }

    suspend fun getVersion(): Result<SystemVersion, ErrorResponse> =
        with(dockerClient) {
            return client.get("/version").parse()
        }

    suspend fun ping(): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.get("/_ping").validateOnly()
        }

    suspend fun dataUsage(): Result<SystemDataUsageResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get("/system/df").parse()
        }

    fun events(
        since: String? = null,
        until: String? = null,
        filters: Map<String, List<String>>? = null
    ): Flow<EventMessage> =
        flow {
            dockerClient.client.prepareGet("/events") {
                since?.let { parameter("since", it) }
                until?.let { parameter("until", it) }
                filters?.let { parameter("filters", dockerClient.json.encodeToString(it)) }
            }.execute { response ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line()
                    if (!line.isNullOrBlank()) {
                        try {
                            emit(dockerClient.json.decodeFromString<EventMessage>(line))
                        } catch (e: Exception) {
                            // Skip invalid lines
                        }
                    }
                }
            }
        }
}
