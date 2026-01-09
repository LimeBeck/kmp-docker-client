import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.api.images
import dev.limebeck.libs.docker.client.api.system
import dev.limebeck.libs.docker.client.api.volumes
import dev.limebeck.libs.docker.client.model.VolumeCreateOptions
import dev.limebeck.libs.logger.LogLevel
import dev.limebeck.libs.logger.logLevel
import dev.limebeck.libs.logger.logger
import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.html.*
import routes.handleLogsStream
import ui.*

class Application

val logger = Application::class.logger()

fun main() {
    logLevel = LogLevel.DEBUG
    val dockerClient = DockerClient()
    embeddedServer(CIO, configure = {
        reuseAddress = true
        connector {
            port = 8080
        }
    }) {
        // Вспомогательная функция для рендеринга страницы или фрагмента
        suspend fun RoutingContext.respondSmart(title: String, block: FlowContent.() -> Unit) {
            val isHtmx = call.request.headers["HX-Request"] == "true"
            if (isHtmx) {
                call.respondHtml { body { block() } }
            } else {
                call.respondHtml { renderLayout(title) { block() } }
            }
        }

        routing {
            get("/") { call.respondRedirect("/containers") }

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
                    val id = call.parameters["id"]!!
                    logger.debug { "Streaming logs for container: $id" }
                    handleLogsStream(dockerClient)
                }
            }

            route("/images") {
                get {
                    logger.debug { "Fetching images list" }
                    val images = dockerClient.images.list().getOrNull() ?: emptyList()
                    respondSmart("Images") { renderImagesPage(images) }
                }
                get("/{id}") {
                    val id = call.parameters["id"]!!
                    logger.debug { "Inspecting image: $id" }
                    val info = dockerClient.images.inspect(id).getOrNull()
                    respondSmart("Image Details") {
                        renderImageDetailsPage(id, info)
                    }
                }
                post("/pull") {
                    val name = call.receiveParameters()["image-pull-name"] ?: ""
                    logger.debug { "Pulling image: $name" }
                    val result = dockerClient.images.create(fromImage = name)

                    result.fold(
                        onSuccess = {
                            logger.debug { "Image $name pulled successfully" }
                            call.respondRedirect("/images")
                        },
                        onError = { error ->
                            logger.error(Exception(error.message)) { "Failed to pull image $name" }
                            val images = dockerClient.images.list().getOrNull() ?: emptyList()
                            call.respondHtml {
                                body {
                                    renderImagesPage(images)

                                    div {
                                        attributes["hx-swap-oob"] = "afterbegin:#alerts"
                                        renderError("Failed to pull image '$name': ${error.message}")
                                    }
                                }
                            }
                        }
                    )
                }
                post("/prune") {
                    logger.debug { "Pruning images" }
                    dockerClient.images.prune()
                    call.respondRedirect("/images")
                }
                delete("/{id}") {
                    val id = call.parameters["id"]!!
                    logger.debug { "Removing image: $id" }
                    dockerClient.images.remove(id)
                    call.respondRedirect("/images")
                }
            }

            route("/volumes") {
                get {
                    logger.debug { "Fetching volumes list" }
                    val volumes = dockerClient.volumes.getList().getOrNull()?.volumes ?: emptyList()
                    respondSmart("Volumes") { renderVolumesPage(volumes) }
                }
                post {
                    val name = call.receiveParameters()["name"] ?: ""
                    logger.debug { "Creating volume: $name" }
                    val result = dockerClient.volumes.create(VolumeCreateOptions(name = name))

                    result.fold(
                        onSuccess = {
                            logger.debug { "Volume $name created successfully" }
                            call.respondRedirect("/volumes")
                        },
                        onError = { error ->
                            logger.error(Exception(error.message)) { "Failed to create volume $name" }
                            val volumes = dockerClient.volumes.getList().getOrNull()?.volumes ?: emptyList()
                            call.respondHtml {
                                body {
                                    renderVolumesPage(volumes)
                                    div {
                                        attributes["hx-swap-oob"] = "afterbegin:#alerts"
                                        renderError("Failed to create volume '$name': ${error.message}")
                                    }
                                }
                            }
                        }
                    )
                }
                post("/prune") {
                    logger.debug { "Pruning volumes" }
                    dockerClient.volumes.prune()
                    call.respondRedirect("/volumes")
                }
                delete("/{name}") {
                    val name = call.parameters["name"]!!
                    logger.debug { "Removing volume: $name" }
                    dockerClient.volumes.remove(name)
                    call.respondRedirect("/volumes")
                }
            }

            route("/system") {
                get {
                    logger.debug { "Fetching system info and version" }
                    val info = dockerClient.system.getInfo().getOrNull()
                    val version = dockerClient.system.getVersion().getOrNull()
                    respondSmart("System Info") { renderSystemPage(info, version) }
                }
                get("/events") {
                    logger.debug { "Subscribing to system events" }
                    call.response.cacheControl(CacheControl.NoCache(null))
                    call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                        dockerClient.system.events().collect { event ->
                            val html =
                                "<div class='py-1 border-b border-gray-800'><span class='text-blue-400'>${event.action}</span> <span class='text-gray-500'>${event.type}</span> ${
                                    event.actor?.attributes?.get("name") ?: ""
                                }</div>"
                            writeStringUtf8("data: $html\n\n")
                            flush()
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}
