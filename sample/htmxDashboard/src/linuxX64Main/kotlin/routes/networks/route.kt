package routes.networks

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.networks
import io.ktor.server.routing.*
import logger
import routes.respondSmart
import routes.pageAction

fun Route.networksRoute(dockerClient: DockerClient) {
    route("/networks") {
        get {
            logger.info { "Fetching networks list" }
            pageAction("Networks") {
            val networks = dockerClient.networks.list().getOrThrow()
            respondSmart("Networks") { renderNetworksPage(networks) }
            }
        }
    }
}
