package routes.images

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.images
import io.ktor.http.ContentType
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.body
import kotlinx.html.div
import logger
import routes.respondSmart
import routes.withHeartbeat
import routes.redirectSmart
import ui.renderError


fun Routing.imagesRoute(dockerClient: DockerClient) {
    route("/images") {
        get {
            logger.info { "Fetching images list" }
            val images = dockerClient.images.list().getOrNull() ?: emptyList()
            respondSmart("Images") { renderImagesPage(images) }
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            logger.info { "Inspecting image: $id" }
            val info = dockerClient.images.inspect(id).getOrNull()
            respondSmart("Image Details") {
                renderImageDetailsPage(id, info)
            }
        }

        post("/pull") {
            val name = call.receiveParameters()["image-pull-name"].orEmpty().trim()
            call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                withHeartbeat("{\"state\":\"heartbeat\",\"message\":\"\"}\n") { send ->
                    suspend fun report(state: String, message: String) {
                        send(dockerClient.json.encodeToString(mapOf("state" to state, "message" to message)) + "\n")
                    }
                    try {
                        require(name.isNotEmpty()) { "Image is required" }
                        val result = dockerClient.images.create(fromImage = name) { progress ->
                            val count = progress.progressDetail?.let { "${it.current ?: 0uL}/${it.total ?: 0uL}" }.orEmpty()
                            report("progress", listOfNotNull(progress.id, progress.status, progress.stream, count.takeIf { it.isNotEmpty() }).joinToString(" "))
                        }
                        result.fold(
                            onSuccess = { report("success", "Complete: $name") },
                            onError = { report("error", "Failed: ${it.message}") },
                        )
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { report("error", "Failed: ${e.message}") }
                }
            }
        }

        post("/prune") {
            logger.info { "Pruning images" }
            dockerClient.images.prune()
            redirectSmart("/images")
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!
            logger.info { "Removing image: $id" }
            dockerClient.images.remove(id)
            redirectSmart("/images")
        }
    }
}
