import dev.limebeck.docker.client.DockerClient
import dev.limebeck.docker.client.model.ContainerLogsParameters
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val dockerClient = DockerClient()

//    val createdContainer = dockerClient.createContainer(
//        "hello-world",
//        config = ContainerConfig(
//            cmd = listOf("bash", "-c", "'while true; do date; sleep 2; done'"),
//            image = "bash"
//        )
//    ).getOrNull()
//
//    println(createdContainer)
//
//    dockerClient.startContainer(createdContainer!!.id).getOrNull()

    val containers = dockerClient.getContainersList().getOrNull()!!
    val id = containers.first().id!!
    val container = dockerClient.getContainerInfo(id).getOrNull()!!
    println(container)

    val logs = dockerClient.getContainerLogs(
        id,
        parameters = ContainerLogsParameters(
            follow = true,
            tail = "10"
        )
    ).getOrNull()!!
    logs.collect {
        println("${it.type}: ${it.line}")
    }
}