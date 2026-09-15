package routes.containers

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.model.*
import io.ktor.http.Parameters
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.html.*
import kotlinx.serialization.json.JsonObject
import routes.respondSmart
import routes.operationError
import routes.pageAction
import kotlin.random.Random

const val MANAGED_LABEL = "dev.limebeck.dashboard.managed"
const val PREVIOUS_LABEL = "dev.limebeck.dashboard.previous"
const val PREVIOUS_RUNNING_LABEL = "dev.limebeck.dashboard.previous-running"

private fun Parameters.configurationParameters(): Parameters = Parameters.build {
    appendAll(this@configurationParameters)
    listOf("env", "ports", "volumes").forEach { name ->
        if (!this@configurationParameters.contains(name)) {
            append(name, this@configurationParameters.getAll("$name-row").orEmpty().filter { it.isNotBlank() }.joinToString("\n"))
        }
    }
}

private fun Parameters.lines(name: String) = get(name).orEmpty().lines().map(String::trim).filter(String::isNotEmpty)

private fun Parameters.configuration(): ContainerCreateRequest {
    val image = get("image").orEmpty().trim()
    checkField("image", image.isNotEmpty(), "Image is required")
    val environment = lines("env")
    checkField("env", environment.all { it.substringBefore('=', "").matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }, "Environment: enter KEY=value")
    val ports = lines("ports").associate { line ->
        val match = Regex("127\\.0\\.0\\.1:(\\d+):(\\d+)(?:/(tcp|udp))?").matchEntire(line)
        if (match == null) throw FormFieldException("ports", "Ports: use 127.0.0.1:host-port:container-port/tcp (or /udp)")
        val host = match.groupValues[1].toIntOrNull() ?: -1
        val target = match.groupValues[2].toIntOrNull() ?: -1
        checkField("ports", host in 0..65535 && target in 1..65535, "Host port must be 0–65535; container port must be 1–65535")
        val key = "$target/${match.groupValues[3].ifEmpty { "tcp" }}"
        key to listOf(PortBinding(hostIp = "127.0.0.1", hostPort = host.toString()))
    }
    val mounts = lines("volumes").map { line ->
        val parts = line.split(':', limit = 2)
        checkField("volumes", parts.size == 2 && parts[0].matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]+")) && parts[1].startsWith('/'), "Volumes: use volume-name:/absolute/container/path")
        Mount(type = Mount.Type.VOLUME, source = parts[0], target = parts[1])
    }
    val network = get("network").orEmpty().trim()
    return ContainerCreateRequest(
        image = image,
        cmd = get("cmd")?.takeIf { it.isNotBlank() }?.lines(),
        env = environment,
        tty = get("tty") == "on",
        openStdin = get("tty") == "on",
        labels = mapOf(MANAGED_LABEL to "true"),
        exposedPorts = ports.mapValues { JsonObject(emptyMap()) },
        hostConfig = HostConfig(mounts = mounts, portBindings = ports),
        networkingConfig = if (network.isEmpty()) null else NetworkingConfig(
            endpointsConfig = mapOf(network to EndpointSettings()),
        ),
    )
}

suspend fun RoutingContext.containerAction(block: suspend () -> Unit) {
    try { block() } catch (e: CancellationException) { throw e }
    catch (e: Exception) { operationError(e.message ?: "Container operation failed") }
}

class FormFieldException(val field: String, message: String) : IllegalArgumentException(message)
private fun checkField(field: String, condition: Boolean, message: String) {
    if (!condition) throw FormFieldException(field, message)
}
private suspend fun RoutingContext.configurationAction(params: Parameters, action: String, block: suspend () -> Unit) {
    try { block() } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        respondSmart("Check container configuration", HttpStatusCode.UnprocessableEntity) {
            renderCreateForm(action = action, submitted = params, error = e.message ?: "Container operation failed", errorField = (e as? FormFieldException)?.field)
        }
    }
}

private suspend fun RoutingContext.openContainer(id: String) {
    if (call.request.headers["HX-Request"] == "true") {
        call.response.headers.append("HX-Redirect", "/containers/$id")
        call.respond(HttpStatusCode.OK)
    } else call.respondRedirect("/containers/$id")
}

fun Route.lifecycleRoutes(client: DockerClient) {
    // Replacement/rollback transitions are serialized within this single-process sample.
    val transitions = Mutex()
    post("/create") {
        val params = call.receiveParameters().configurationParameters()
        configurationAction(params, "/containers/create") {
            val id = client.containers.create(
                name = params["name"]?.trim()?.takeIf { it.isNotEmpty() },
                config = params.configuration(),
            ).getOrThrow().id
            try { client.containers.start(id).getOrThrow() }
            catch (e: Exception) {
                withContext(NonCancellable) { client.containers.remove(id, force = true).getOrThrow() }
                throw e
            }
            openContainer(id)
        }
    }
    get("/{id}/recreate") {
        pageAction("Recreate container") {
            val id = call.parameters["id"]!!
            val info = client.containers.getInfo(id).getOrThrow()
            require(info.config?.labels?.get(MANAGED_LABEL) == "true") {
                "Recreate is available for containers configured by this sample. Other configurations must be supplied explicitly by their application."
            }
            require(info.config?.labels?.get(PREVIOUS_LABEL)?.let { client.containers.getInfo(it).isSuccess } != true) { "Finish or roll back the pending replacement first" }
            respondSmart("Recreate container") { renderCreateForm(info, "/containers/$id/recreate") }
        }
    }
    post("/{id}/recreate") {
        val params = call.receiveParameters().configurationParameters()
        configurationAction(params, "/containers/${call.parameters["id"]!!}/recreate") {
            val desired = params.configuration()
            transitions.withLock {
                val id = call.parameters["id"]!!
                val old = client.containers.getInfo(id).getOrThrow()
                require(old.config?.labels?.get(MANAGED_LABEL) == "true") { "Only sample-managed containers can be recreated" }
                require(old.config?.labels?.get(PREVIOUS_LABEL)?.let { client.containers.getInfo(it).isSuccess } != true) { "Finish or roll back the pending replacement first" }
                require(client.containers.getList(all = true, filters = mapOf("label" to listOf("$PREVIOUS_LABEL=$id"))).getOrThrow().isEmpty()) {
                    "A replacement already exists; finish or roll it back first"
                }
                val wasRunning = old.state?.running == true
                // Prepare before stopping the original: missing-image/configuration failures leave it serving.
                val candidate = client.containers.create(
                    name = "${old.name?.removePrefix("/")}-candidate-${Random.nextInt(100000, 999999)}",
                    config = desired.copy(labels = desired.labels.orEmpty() + mapOf(
                        PREVIOUS_LABEL to id, PREVIOUS_RUNNING_LABEL to wasRunning.toString(),
                    )),
                ).getOrThrow().id
                try {
                    if (wasRunning) client.containers.stop(id).getOrThrow()
                    client.containers.start(candidate).getOrThrow()
                    // Running is not application readiness. The user verifies the candidate before committing.
                } catch (e: Exception) {
                    withContext(NonCancellable) {
                        val removalFailure = runCatching { client.containers.remove(candidate, force = true).getOrThrow() }.exceptionOrNull()
                        val restartFailure = if (wasRunning) runCatching { client.containers.start(id).getOrThrow() }.exceptionOrNull() else null
                        listOfNotNull(removalFailure, restartFailure).forEach(e::addSuppressed)
                        if (e !is CancellationException && (removalFailure != null || restartFailure != null)) {
                            error("${e.message}; rollback needs attention: ${listOfNotNull(removalFailure, restartFailure).joinToString { it.message.orEmpty() }}")
                        }
                    }
                    throw e
                }
                openContainer(candidate)
            }
        }
    }
    post("/{id}/rollback") {
        containerAction {
            transitions.withLock {
                val id = call.parameters["id"]!!
                val current = client.containers.getInfo(id).getOrThrow()
                val labels = current.config?.labels.orEmpty()
                val previous = requireNotNull(labels[PREVIOUS_LABEL]) { "No pending replacement" }
                client.containers.getInfo(previous).getOrThrow()
                client.containers.stop(id).getOrThrow()
                if (labels[PREVIOUS_RUNNING_LABEL] == "true") client.containers.start(previous).getOrThrow()
                client.containers.remove(id).getOrThrow()
                openContainer(previous)
            }
        }
    }
    post("/{id}/confirm") {
        containerAction {
            transitions.withLock {
                val id = call.parameters["id"]!!
                val candidate = client.containers.getInfo(id).getOrThrow()
                val previous = requireNotNull(candidate.config?.labels?.get(PREVIOUS_LABEL)) { "No pending replacement" }
                require(candidate.state?.running == true) { "Candidate is not running; verify readiness or roll back" }
                val old = client.containers.getInfo(previous).getOrThrow()
                require(old.state?.running != true) { "Previous container must be stopped" }
                // The user explicitly confirmed readiness. Named volume data is never removed here.
                client.containers.remove(previous).getOrThrow()
                openContainer(id)
            }
        }
    }
}
