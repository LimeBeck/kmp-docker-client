import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.DockerClientConfig
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

fun main(args: Array<String>) {
    require(args.size <= 2) { "Usage: htmxDashboard.kexe [docker-socket] [http-port]" }
    val socket = args.getOrNull(0) ?: "/var/run/docker.sock"
    val httpPort = args.getOrNull(1)?.toInt() ?: 8080
    require(httpPort in 1..65535) { "HTTP port must be between 1 and 65535" }
    logLevel = LogLevel.DEBUG
    val dockerClient = DockerClient(DockerClientConfig(
        connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(socket),
    ))
    embeddedServer(CIO, configure = {
        reuseAddress = true
        connector {
            host = "127.0.0.1"
            port = httpPort
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
