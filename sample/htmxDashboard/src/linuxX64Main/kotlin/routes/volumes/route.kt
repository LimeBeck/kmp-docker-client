package routes.volumes

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.volumes
import dev.limebeck.libs.docker.client.model.VolumeCreateOptions
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.body
import kotlinx.html.div
import logger
import routes.respondSmart
import ui.renderError

fun Route.volumesRoute(dockerClient: DockerClient) {
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
}