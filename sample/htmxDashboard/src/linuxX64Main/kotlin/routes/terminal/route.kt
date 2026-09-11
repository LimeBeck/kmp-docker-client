package routes.terminal

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import routes.bridgeTerminal
import routes.respondSmart

fun Routing.terminalRoute(dockerClient: DockerClient) {
    route("/containers/{id}/terminal") {
        get {
            val id = call.parameters["id"]!!
            val info = dockerClient.containers.getInfo(id).getOrNull()
            respondSmart("Terminal") {
                renderTerminal(id, info)
            }
        }

        webSocket("/ws") {
            val containerId = call.parameters["id"]!!

            val session = dockerClient.containers.attach(
                id = containerId,
                stdin = true,
                stdout = true,
                stderr = true,
                stream = true,
                logs = true,
            ).getOrThrow()

            session.use {
                dockerClient.containers.start(containerId).getOrThrow()
                bridgeTerminal(it)
            }
        }
    }
}
