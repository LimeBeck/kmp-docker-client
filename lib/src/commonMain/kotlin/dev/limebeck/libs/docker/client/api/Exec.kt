package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.utils.createInteractiveSession
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.ExperimentalUuidApi

/** Cached exec API bound to this client and its connection configuration. */
val DockerClient.exec by ::Exec.api()

/**
 * Docker exec operations using the owning [DockerClient].
 *
 * Result-returning methods report daemon HTTP errors as [ErrorResponse]. Transport/decoding failures
 * and cancellation can throw. Live flows report request failures during collection.
 */
class Exec(private val dockerClient: DockerClient) {
    /**
     * Starts an existing exec instance using the supplied configuration and discards response output.
     * Set config.detach=true for detached execution; use [startInteractive] when output is needed.
     * Successful start does not establish the command exit status; inspect it with [getInfo].
     *
     * @param id Exec instance ID returned by Containers.execCreate.
     * @param config Configuration sent to Docker as JSON.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun startAndForget(
        id: String,
        config: ExecStartConfig = ExecStartConfig()
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        client.post(apiPath("/exec/$id/start")) {
            contentType(ContentType.Application.Json)
            setBody(config)
        }.validateOnly()
    }

    /**
     * Starts a previously created exec instance and returns an independently owned [ExecSession].
     * Match the TTY mode used at creation; the overload without tty uses true.
     * Collect one output flow once. Use incomingChunks for terminal bytes and incremental UTF-8 decoding.
     * Close the session when finished, including when output is never collected.
     *
     * Transport and non-HTTP handshake failures throw their original exception; read
     * [dev.limebeck.libs.docker.client.diagnostics.dockerContext] for safe operation details.
     * Cancellation propagates. Output and send failures can also occur after this method returns.
     *
     * @param id Exec instance ID returned by Containers.execCreate.
     * @param consoleSize Initial TTY dimensions as rows to columns; null leaves Docker defaults.
     * @sample dev.limebeck.libs.docker.guide.openShell
     * @see Containers.execCreate
     * @return Owned interactive session, or a daemon HTTP error response.
     */
    suspend fun startInteractive(
        id: String,
        consoleSize: Pair<Int, Int>? = null
    ): Result<ExecSession, ErrorResponse> = startInteractive(id, consoleSize, tty = true)

    /**
     * Starts a previously created exec instance and returns an independently owned [ExecSession].
     * Match the TTY mode used at creation; the overload without tty uses true.
     * Collect one output flow once. Use incomingChunks for terminal bytes and incremental UTF-8 decoding.
     * Close the session when finished, including when output is never collected.
     *
     * Transport and non-HTTP handshake failures throw their original exception; read
     * [dev.limebeck.libs.docker.client.diagnostics.dockerContext] for safe operation details.
     * Cancellation propagates. Output and send failures can also occur after this method returns.
     *
     * @param id Exec instance ID returned by Containers.execCreate.
     * @param consoleSize Initial TTY dimensions as rows to columns; null leaves Docker defaults.
     * @param tty TTY mode matching the original exec configuration.
     * @sample dev.limebeck.libs.docker.guide.openShell
     * @see Containers.execCreate
     * @return Owned interactive session, or a daemon HTTP error response.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun startInteractive(
        id: String,
        consoleSize: Pair<Int, Int>? = null,
        tty: Boolean
    ): Result<ExecSession, ErrorResponse> = with(dockerClient) {
        coroutineScope {
            val config = ExecStartConfig(
                detach = false,
                tty = tty,
                consoleSize = consoleSize?.let { listOf(it.first, it.second) }
            )
            val body = dockerClient.json.encodeToString(config)
            val encodedBody = body.encodeToByteArray()

            return@coroutineScope createInteractiveSession(
                tty = tty,
                method = HttpMethod.Post,
                path = "/exec/$id/start",
                headers = buildMap {
                    set("Host", "docker")
                    set("Content-Type", "application/json")
                    set("Connection", "Upgrade")
                    set("Upgrade", "tcp")
                },
                body = encodedBody
            )
        }
    }

    /**
     * Inspect an exec instance
     *
     * Return low-level information about an exec instance.
     *
     * @param id Exec instance ID returned by Containers.execCreate.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun getInfo(id: String): Result<ExecInspectResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get(apiPath("/exec/$id/json")).parse()
        }

    /**
     * Resize an exec instance
     *
     * Resize the TTY session used by an exec instance. This endpoint only works if `tty` was specified as `true`
     * when creating the exec instance.
     *
     * @param id Exec instance ID returned by Containers.execCreate.
     * @param h Terminal height in rows.
     * @param w Terminal width in columns.
     * @return Operation response on success, or the Docker error response. Transport failures and cancellation can throw.
     */
    suspend fun resize(
        id: String,
        h: Int,
        w: Int
    ): Result<Unit, ErrorResponse> =
        with(dockerClient) {
            return client.post(apiPath("/exec/$id/resize")) {
                parameter("h", h.toString())
                parameter("w", w.toString())
            }.validateOnly()
        }
}
