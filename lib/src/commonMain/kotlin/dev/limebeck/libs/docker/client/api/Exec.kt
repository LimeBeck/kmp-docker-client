package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.utils.createInteractiveSession
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.ExperimentalUuidApi

val DockerClient.exec by ::Exec.api()

class Exec(private val dockerClient: DockerClient) {
    /**
     * Start an exec instance
     *
     * Starts a previously set up exec instance. If detach is true, this endpoint returns immediately after starting
     * the command.
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
     * Start an exec instance
     *
     * Starts a previously set up exec instance. If detach is true, this endpoint returns immediately after starting
     * the command.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun startInteractive(
        id: String,
        consoleSize: Pair<Int, Int>? = null
    ): Result<ExecSession, ErrorResponse> = with(dockerClient) {
        coroutineScope {
            val config = ExecStartConfig(
                detach = false,
                tty = true,
                consoleSize = consoleSize?.let { listOf(it.first, it.second) }
            )
            val body = dockerClient.json.encodeToString(config)
            val encodedBody = body.encodeToByteArray()

            return@coroutineScope createInteractiveSession(
                tty = true,
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
