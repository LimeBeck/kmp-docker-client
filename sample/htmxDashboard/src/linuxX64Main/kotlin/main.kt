import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.logger.LogLevel
import dev.limebeck.libs.logger.logLevel
import dev.limebeck.libs.logger.logger
import io.ktor.server.application.install
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets
import routes.containers.containersRoute
import routes.exec.execRoute
import routes.images.imagesRoute
import routes.networks.networksRoute
import routes.system.systemRoute
import routes.terminal.terminalRoute
import routes.volumes.volumesRoute

class Application

val logger = Application::class.logger()

fun main() {
    logLevel = LogLevel.DEBUG
    val dockerClient = DockerClient()
    embeddedServer(CIO, configure = {
        reuseAddress = true
        connector {
            host = "127.0.0.1"
            port = 8080
        }
    }) {
        install(WebSockets)
        routing {
            get("/") { call.respondRedirect("/system") }

            containersRoute(dockerClient)
            execRoute(dockerClient)
            imagesRoute(dockerClient)
            volumesRoute(dockerClient)
            systemRoute(dockerClient)
            networksRoute(dockerClient)
            terminalRoute(dockerClient)
        }
    }.start(wait = true)
}
