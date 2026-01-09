package routes.images

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.images
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.body
import kotlinx.html.div
import logger
import routes.respondSmart
import ui.renderError


fun Routing.imagesRoute(dockerClient: DockerClient) {
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
}