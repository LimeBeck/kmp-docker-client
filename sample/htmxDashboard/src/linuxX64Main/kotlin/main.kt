import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.logger.LogLevel
import dev.limebeck.libs.logger.logLevel
import dev.limebeck.libs.logger.logger
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import routes.containers.containersRoute
import routes.images.imagesRoute
import routes.system.systemRoute
import routes.volumes.volumesRoute

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

        routing {
            get("/") { call.respondRedirect("/system") }

            containersRoute(dockerClient)
            imagesRoute(dockerClient)
            volumesRoute(dockerClient)
            systemRoute(dockerClient)
        }
    }.start(wait = true)
}
