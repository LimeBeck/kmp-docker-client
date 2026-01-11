package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.dsl.api
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.utils.prependLeftover
import dev.limebeck.libs.docker.client.utils.readHttp11Headers
import dev.limebeck.libs.docker.client.utils.readLogLines
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
        client.post("/exec/$id/start") {
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
        config: ExecStartConfig = ExecStartConfig(tty = true)
    ): Result<ExecSession, ErrorResponse> = with(dockerClient) {
        coroutineScope {
            val execInfo = getInfo(id).getOrNull()
            val isTty = execInfo?.processConfig?.tty == true || config.tty == true

            val conn = openRawConnection()

            try {
                val body = dockerClient.json.encodeToString(config)
                val encodedBody = body.encodeToByteArray()

                // Build hijack headers
                val request = buildString {
                    append("POST /exec/$id/start HTTP/1.1\r\n")
                    append("Host: docker\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Upgrade: tcp\r\n")
                    append("Content-Length: ${encodedBody.size}\r\n")
                    append("\r\n")
                }

                conn.write.writeFully(request.encodeToByteArray())
                conn.write.writeFully(encodedBody)
                conn.write.flush()

                DockerClient.logger.debug { "Send request: \n$request$body" }

                val hs = readHttp11Headers(conn.read)

                // Typically 101, but sometimes may be 200.
                if (hs.status != 101 && hs.status != 200) {
                    conn.close()
                    return@coroutineScope Result.error(
                        ErrorResponse(
                            message = "Docker hijack failed: HTTP ${hs.status}",
                        )
                    )
                }

                val incomingChannel = prependLeftover(hs.leftover, conn.read)

                val incomingFlow: Flow<LogLine> = flow {
                    incomingChannel.readLogLines(isTty, this@flow)
                }

                val session = ExecSession(incomingFlow, conn)

                return@coroutineScope Result.success(session)
            } catch (t: Throwable) {
                runCatching { conn.close() }
                return@coroutineScope Result.error(
                    ErrorResponse(message = t.message ?: "startInteractive failed")
                )
            }
        }
    }

    /**
     * Inspect an exec instance
     *
     * Return low-level information about an exec instance.
     */
    suspend fun getInfo(id: String): Result<ExecInspectResponse, ErrorResponse> =
        with(dockerClient) {
            return client.get("/exec/$id/json").parse()
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
            return client.post("/exec/$id/resize") {
                parameter("h", h.toString())
                parameter("w", w.toString())
            }.validateOnly()
        }
}
