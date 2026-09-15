package routes.volumes

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.volumes
import dev.limebeck.libs.docker.client.model.VolumeCreateOptions
import io.ktor.server.request.*
import io.ktor.server.routing.*
import routes.*
import routes.containers.containerAction

fun Route.volumesRoute(dockerClient: DockerClient) {
    route("/volumes") {
        get { pageAction("Volumes") { val volumes = dockerClient.volumes.getList().getOrThrow().volumes.orEmpty(); respondSmart("Volumes") { renderVolumesPage(volumes) } } }
        post { containerAction {
            val name = call.receiveParameters()["name"].orEmpty().trim()
            dockerClient.volumes.create(VolumeCreateOptions(name = name)).getOrThrow()
            redirectSmart("/volumes?notice=created")
        } }
        post("/prune") { containerAction { dockerClient.volumes.prune().getOrThrow(); redirectSmart("/volumes?notice=pruned") } }
        delete("/{name}") { containerAction { dockerClient.volumes.remove(call.parameters["name"]!!).getOrThrow(); redirectSmart("/volumes?notice=removed") } }
    }
}
