import arrow.continuations.SuspendApp
import dev.limebeck.docker.client.DockerClient
import dev.limebeck.docker.client.api.containers
import dev.limebeck.docker.client.model.ContainerConfig
import dev.limebeck.docker.client.model.ContainerLogsParameters
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

fun main() = SuspendApp {
    val dockerClient = DockerClient()

    dockerClient.containers.remove("hello-world", force = true).onError {
        println(it.message)
    }.onSuccess {
        println("Removed existing container hello-world")
    }

    val createdContainer = dockerClient.containers.create(
        name = "hello-world",
        config = ContainerConfig(
            cmd = listOf("-c", "while true; do date; sleep 2; done"),
            image = "bash"
        )
    ).onError { println(it.message) }.getOrNull()

    println(createdContainer)

    dockerClient.containers.start(createdContainer!!.id).getOrNull()

    try {
        val containers = dockerClient.containers.getList().getOrNull()!!
        println(containers)
        val id = createdContainer.id
        val container = dockerClient.containers.getInfo(id).getOrNull()!!
        println(container)

        val logs = dockerClient.containers.getLogs(
            id = id,
            parameters = ContainerLogsParameters(
                follow = true,
                tail = "10"
            )
        ).getOrNull()!!
        logs.collect {
            println("${it.type}: ${it.line}")
        }
    } catch (e: CancellationException) {
        withContext(NonCancellable) {
            dockerClient.containers.stop(createdContainer.id, signal = "SIGINT", t = 10).getOrNull()
            println("Stopped container ${createdContainer.id}")
            dockerClient.containers.remove(createdContainer.id).getOrNull()
            println("Removed container ${createdContainer.id}")
        }
    }
}
