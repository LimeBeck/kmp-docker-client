package routes.containers

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.model.ContainerLogsParameters
import dev.limebeck.libs.docker.client.model.LogLine
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import logger
import routes.respondSmart
import ui.escapeHtml
import ui.renderError

fun Routing.containersRoute(dockerClient: DockerClient) {
    route("/containers") {
        get {
            logger.debug { "Fetching containers list" }
            val containers = dockerClient.containers.getList(true).getOrNull() ?: emptyList()
            respondSmart("Containers") {
                h1("text-3xl font-bold mb-6 text-blue-400") { +"🐳 Containers" }
                containerTable(containers)
            }
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            logger.debug { "Fetching container info for id: $id" }
            val info = dockerClient.containers.getInfo(id).getOrNull()
            respondSmart("Container Details") {
                renderContainerDetailsPage(id, info)
            }
        }

        post("/{id}/start") {
            val containerId = call.parameters["id"]!!
            logger.debug { "Starting container: $containerId" }
            val result = dockerClient.containers.start(containerId)

            result.fold(
                onSuccess = {
                    logger.debug { "Container $containerId started successfully" }
                    call.respondRedirect("/containers/$containerId")
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
            logger.debug { "Stopping container: $containerId" }
            val result = dockerClient.containers.stop(containerId)

            result.fold(
                onSuccess = {
                    logger.debug { "Container $containerId stopped successfully" }
                    call.respondRedirect("/containers/$containerId")
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
            logger.debug { "Removing container: $containerId" }
            val result = dockerClient.containers.remove(containerId, force = true)

            result.fold(
                onSuccess = {
                    logger.debug { "Container $containerId removed successfully" }
                    call.respondRedirect("/containers")
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
            logger.debug { "Streaming logs for container: $id" }
            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                try {
                    val logsFlow = dockerClient.containers.getLogs(
                        id = id,
                        parameters = ContainerLogsParameters(follow = true, stdout = true, stderr = true, tail = "20")
                    ).getOrThrow()

                    logsFlow.collect { log ->
                        val color = if (log.type == LogLine.Type.STDERR) "text-red-400" else "text-gray-400"
                        val html =
                            "<div class='leading-relaxed'><span class='$color'>${log.line.escapeHtml()}</span></div>"
                        writeStringUtf8("data: $html\n\n")
                        flush()
                    }
                } catch (e: Exception) {
                    writeStringUtf8("data: <div class='text-orange-500 italic'>Stream disconnected</div>\n\n")
                    flush()
                }
            }
        }
    }
}