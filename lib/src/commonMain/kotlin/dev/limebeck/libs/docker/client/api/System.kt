package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.utils.readDockerLine

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.serialization.SerializationException

/** Cached system API bound to this client and its connection configuration. */
val DockerClient.system by ::System.api()

/**
 * Docker system operations using the owning [DockerClient].
 *
 * Result-returning methods report daemon HTTP errors as [ErrorResponse]. Transport/decoding failures
 * and cancellation can throw. Live flows report request failures during collection.
 */
class System(private val dockerClient: DockerClient) {
    /**
     * Get system information
     *
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getInfo(): Result<SystemInfo, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/info")).parse()
        }

    /**
     * Get version
     *
     * Returns the version of Docker that is running and various information about the system that Docker is running on.
     *
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getVersion(): Result<SystemVersion, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/version")).parse()
        }

    /**
     * Ping
     *
     * This is a dummy endpoint you can use to test if the server is accessible.
     *
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun ping(): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/_ping")).validateOnly()
        }

    /**
     * Get data usage information
     *
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun dataUsage(): Result<SystemDataUsageResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/system/df")).parse()
        }

    /**
     * Returns a cold event flow: each collection opens a separate request. HTTP errors throw
     * [dev.limebeck.libs.docker.client.model.DockerApiException]. Malformed JSON events are skipped;
     * transport and collector failures propagate. Cancellation or completion releases the request.
     * There is no automatic reconnect. Docker history is finite: save a cursor, deduplicate replay and
     * refresh resource state after reconnect. An interrupted connection may also finish as normal EOF.
     *
     * @param since Start timestamp accepted by Docker; null requests new events only.
     * @param until End timestamp accepted by Docker; null leaves the subscription live.
     * @param filters Docker filter names mapped to accepted values; encoded as JSON by the SDK.
     * @return Cold flow of daemon events; failures are reported during collection.
     */
    fun events(
        since: String? = null,
        until: String? = null,
        filters: Map<String, List<String>>? = null
    ): Flow<EventMessage> = with(dockerClient) {
        channelFlow {
            client.prepareGet(apiPath("/events")) {
                applyStreamConfig()
                since?.let { parameter("since", it) }
                until?.let { parameter("until", it) }
                filters?.let { parameter("filters", json.encodeToString(it)) }
            }.execute { response ->
                response.consumeStream { channel ->
                    while (true) {
                        val line = channel.readDockerLine() ?: break
                        if (line.isBlank()) continue
                        val event = try {
                            json.decodeFromString<EventMessage>(line)
                        } catch (_: SerializationException) {
                            continue
                        }
                        send(event)
                    }
                }
            }
        }.buffer(0)
    }
}
