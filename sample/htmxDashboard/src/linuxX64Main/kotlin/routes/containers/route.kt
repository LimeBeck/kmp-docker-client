package routes.containers

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.model.ContainerLogsParameters
import dev.limebeck.libs.docker.client.model.ExecConfig
import dev.limebeck.libs.docker.client.model.LogLine
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import logger
import routes.respondSmart
import routes.withHeartbeat
import routes.redirectSmart
import ui.escapeHtml
import ui.renderError

fun Routing.containersRoute(dockerClient: DockerClient) {
    route("/containers") {
        get {
            logger.info { "Fetching containers list" }
            val containers = dockerClient.containers.getList(true).getOrThrow()
            respondSmart("Containers") {
                h1("text-3xl font-bold mb-6 text-blue-400") { +"🐳 Containers" }
                renderCreateForm()
                containerTable(containers)
            }
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            logger.info { "Fetching container info for id: $id" }
            containerAction {
                val info = dockerClient.containers.getInfo(id).getOrThrow()
                val previous = info.config?.labels?.get(PREVIOUS_LABEL)
                val pending = previous != null && dockerClient.containers.getInfo(previous).isSuccess
                respondSmart("Container Details") { renderContainerDetailsPage(id, info, pending) }
            }
        }

        lifecycleRoutes(dockerClient)

        post("/{id}/start") {
            val containerId = call.parameters["id"]!!
            logger.info { "Starting container: $containerId" }
            val result = dockerClient.containers.start(containerId)

            result.fold(
                onSuccess = {
                    logger.info { "Container $containerId started successfully" }
                    redirectSmart("/containers/$containerId")
                },
                onError = { error ->
                    logger.error(Exception(error.message)) { "Failed to start container $containerId" }
                    val containers = dockerClient.containers.getList(all = true).getOrNull() ?: emptyList()
                    call.respondHtml {
                        body {
                            h1("text-3xl font-bold mb-6 text-blue-400") { +"🐳 Containers" }
                            div("mb-8") { id = "container-details" }
                            containerTable(containers)
                            div {
                                attributes["hx-swap-oob"] = "afterbegin:#alerts"
                                renderError("Failed to start container: ${error.message}")
                            }
                        }
                    }
                }
            )
        }

        post("/{id}/stop") {
            val containerId = call.parameters["id"]!!
            logger.info { "Stopping container: $containerId" }
            val result = dockerClient.containers.stop(containerId)

            result.fold(
                onSuccess = {
                    logger.info { "Container $containerId stopped successfully" }
                    redirectSmart("/containers/$containerId")
                },
                onError = { error ->
                    logger.error(Exception(error.message)) { "Failed to stop container $containerId" }
                    val containers = dockerClient.containers.getList(all = true).getOrNull() ?: emptyList()
                    call.respondHtml {
                        body {
                            h1("text-3xl font-bold mb-6 text-blue-400") { +"🐳 Containers" }
                            div("mb-8") { id = "container-details" }
                            containerTable(containers)
                            div {
                                attributes["hx-swap-oob"] = "afterbegin:#alerts"
                                renderError("Failed to stop container: ${error.message}")
                            }
                        }
                    }
                }
            )
        }

        delete("/{id}") {
            val containerId = call.parameters["id"]!!
            logger.info { "Removing container: $containerId" }
            val result = dockerClient.containers.remove(containerId, force = true)

            result.fold(
                onSuccess = {
                    logger.info { "Container $containerId removed successfully" }
                    redirectSmart("/containers")
                },
                onError = { error ->
                    logger.error(Exception(error.message)) { "Failed to remove container $containerId" }
                    val containers = dockerClient.containers.getList(all = true).getOrNull() ?: emptyList()
                    call.respondHtml {
                        body {
                            h1("text-3xl font-bold mb-6 text-blue-400") { +"🐳 Containers" }
                            div("mb-8") { id = "container-details" }
                            containerTable(containers)
                            div {
                                attributes["hx-swap-oob"] = "afterbegin:#alerts"
                                renderError("Failed to remove container: ${error.message}")
                            }
                        }
                    }
                }
            )
        }

        get("/{id}/logs") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            logger.info { "Streaming logs for container: $id" }
            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                withHeartbeat { send ->
                    try {
                        val logsFlow = dockerClient.containers.getLogs(
                            id = id,
                            parameters = ContainerLogsParameters(follow = true, stdout = true, stderr = true, tail = "20")
                        ).getOrThrow()

                        logsFlow.collect { log ->
                            val color = if (log.type == LogLine.Type.STDERR) "text-red-400" else "text-gray-400"
                            val html =
                                "<div class='leading-relaxed'><span class='$color'>${log.line.escapeHtml().replace("\n", "<br>")}</span></div>"
                            send("data: $html\n\n")
                        }
                        send("event: done\ndata: end\n\n")
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        send("data: <div class='text-orange-500 italic'>Stream disconnected</div>\n\n")
                    }
                }
            }
        }

        get("/{id}/stats") {
            val id = call.parameters["id"]!!
            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                withHeartbeat { send ->
                    dockerClient.containers.getStats(id).getOrThrow().collect { stats ->
                        val memory = stats.memoryStats?.usage?.toString() ?: "unavailable"
                        val limit = stats.memoryStats?.limit?.toString() ?: "unavailable"
                        send("data: Memory: $memory / $limit bytes<br>Read: ${stats.read}\n\n")
                    }
                    send("event: done\ndata: end\n\n")
                }
            }
        }

        post("/{id}/exec") {
            val id = call.parameters["id"]!!
            val command = call.receiveParameters()["command"]?.trim()?.takeIf { it.isNotEmpty() }
            val exec = dockerClient.containers.execCreate(
                id, ExecConfig(
                    attachStdin = true,
                    attachStdout = true,
                    attachStderr = true,
                    cmd = command?.split(" ") ?: listOf("/bin/sh"),
                    tty = true
                )
            ).getOrThrow()
            call.response.headers.append("HX-Redirect", "/exec/${exec.id}")
            call.respond(HttpStatusCode.OK)
        }
    }
}
