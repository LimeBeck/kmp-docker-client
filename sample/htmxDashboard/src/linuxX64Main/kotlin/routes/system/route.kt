package routes.system

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.system
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import logger
import routes.respondSmart

fun Routing.systemRoute(dockerClient: DockerClient) {
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