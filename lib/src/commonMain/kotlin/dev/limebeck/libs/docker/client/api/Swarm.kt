package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.api.swarm.*
import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import io.ktor.client.request.*
import io.ktor.http.*

import dev.limebeck.libs.docker.client.dsl.api

/** Cached Swarm API using this client's endpoint and lifetime. */
val DockerClient.swarm by ::Swarm.api()

/**
 * Swarm resource APIs. Accessing this group does not initialize or join a swarm.
 * Resource operations require a manager. No operation closes the supplied client.
 * HTTP errors return Result errors; transport/decoding failures and cancellation throw. No automatic retries.
 */
class Swarm(private val dockerClient: DockerClient) {
    /** Secret metadata and lifecycle operations. */
    val secrets: Secrets by lazy { Secrets(dockerClient) }

    /** Config data, metadata and lifecycle operations. */
    val configs: Configs by lazy { Configs(dockerClient) }

    /** Cluster nodes. */
    val nodes: Nodes by lazy { Nodes(dockerClient) }

    /** Service specifications and logs. */
    val services: Services by lazy { Services(dockerClient) }

    /** Scheduled tasks and logs. */
    val tasks: Tasks by lazy { Tasks(dockerClient) }

    /** Inspects the cluster, including sensitive join tokens. Requires a manager. */
    suspend fun getInfo(): Result<dev.limebeck.libs.docker.client.model.Swarm, ErrorResponse> = with(dockerClient) {
        client.get(apiPath("/swarm")).parse()
    }

    /** Explicitly initializes a cluster on this daemon; returns its node ID. Supply a listen address. */
    suspend fun init(request: SwarmInitRequest): Result<String, ErrorResponse> = with(dockerClient) {
        client.post(apiPath("/swarm/init")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.parse()
    }

    /** Explicitly joins this daemon using a sensitive join token. */
    suspend fun join(request: SwarmJoinRequest): Result<Unit, ErrorResponse> = with(dockerClient) {
        client.post(apiPath("/swarm/join")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.validateOnly()
    }

    /** Leaves the swarm. Force can break manager quorum and is never enabled implicitly. */
    suspend fun leave(force: Boolean = false): Result<Unit, ErrorResponse> = with(dockerClient) {
        client.post(apiPath("/swarm/leave")) { parameter("force", force) }.validateOnly()
    }

    /** Replaces the cluster spec at its inspected version, optionally rotating credentials. */
    suspend fun update(
        version: ULong, spec: SwarmSpec, rotateWorkerToken: Boolean = false,
        rotateManagerToken: Boolean = false, rotateManagerUnlockKey: Boolean = false,
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        client.post(apiPath("/swarm/update")) {
            parameter("version", version.toString())
            parameter("rotateWorkerToken", rotateWorkerToken)
            parameter("rotateManagerToken", rotateManagerToken)
            parameter("rotateManagerUnlockKey", rotateManagerUnlockKey)
            contentType(ContentType.Application.Json)
            setBody(spec)
        }.validateOnly()
    }

    /** Returns the sensitive manager unlock key. Requires a manager. */
    suspend fun getUnlockKey(): Result<UnlockKeyResponse, ErrorResponse> = with(dockerClient) {
        client.get(apiPath("/swarm/unlockkey")).parse()
    }

    /** Unlocks a locked manager using its key. */
    suspend fun unlock(request: SwarmUnlockRequest): Result<Unit, ErrorResponse> = with(dockerClient) {
        client.post(apiPath("/swarm/unlock")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.validateOnly()
    }
}
