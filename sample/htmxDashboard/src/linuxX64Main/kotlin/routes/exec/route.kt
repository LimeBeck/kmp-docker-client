package routes.exec

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.api.exec
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import routes.bridgeTerminal
import routes.respondSmart

fun Routing.execRoute(dockerClient: DockerClient) {
    route("/exec") {
        get("/{execId}") {
            val execId = call.parameters["execId"]!!
            val execInfo = dockerClient.exec.getInfo(execId).getOrThrow()
            val containerId = execInfo.containerID!!
            val info = dockerClient.containers.getInfo(containerId).getOrNull()
            respondSmart("Exec") {
                renderExecTerminal(containerId, execId, info)
            }
        }

        webSocket("/{execId}/ws") {
            val execId = call.parameters["execId"]!!

            val execConnection = dockerClient.exec.startInteractive(execId).getOrThrow()

            execConnection.use { bridgeTerminal(it) }
        }
    }
}
