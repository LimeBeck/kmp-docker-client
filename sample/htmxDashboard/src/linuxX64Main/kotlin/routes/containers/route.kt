package routes.containers

import ui.pageHeading
import ui.pageLink

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.model.ContainerLogsParameters
import dev.limebeck.libs.docker.client.model.ExecConfig
import dev.limebeck.libs.docker.client.model.LogLine
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import logger
import routes.respondSmart
import routes.pageAction
import routes.withHeartbeat
import routes.redirectSmart
import ui.escapeHtml
import ui.renderError

fun Routing.containersRoute(dockerClient: DockerClient) {
    route("/containers") {
        get {
            pageAction("Containers") {
                val containers = dockerClient.containers.getList(true).getOrThrow()
                respondSmart("Containers") {
                    pageHeading("Containers", "${containers.count { it.state == dev.limebeck.libs.docker.client.model.ContainerSummary.State.RUNNING }} running · ${containers.size} total") {
                        pageLink("Create container", "/containers/create", "btn btn-primary")
                    }
                    containerTable(containers)
                }
            }
        }
        get("/create") { respondSmart("Create container") { renderCreateForm() } }

        get("/{id}") {
            val id = call.parameters["id"]!!
            logger.info { "Fetching container info for id: $id" }
            pageAction("Container details") {
                val info = dockerClient.containers.getInfo(id).getOrThrow()
                val previous = info.config?.labels?.get(PREVIOUS_LABEL)
                val pending = previous != null && dockerClient.containers.getInfo(previous).isSuccess
                respondSmart("Container Details") { renderContainerDetailsPage(id, info, pending, call.request.queryParameters["tab"].orEmpty().takeIf { it in listOf("overview", "logs", "terminal", "configuration") } ?: "overview") }
            }
        }

        lifecycleRoutes(dockerClient)

        post("/{id}/start") {
            containerAction {
                val id = call.parameters["id"]!!
                dockerClient.containers.start(id).getOrThrow()
                redirectSmart("/containers/$id?notice=started")
            }
        }
        post("/{id}/stop") {
            containerAction {
                val id = call.parameters["id"]!!
                dockerClient.containers.stop(id).getOrThrow()
                redirectSmart("/containers/$id?notice=stopped")
            }
        }
        delete("/{id}") {
            containerAction {
                dockerClient.containers.remove(call.parameters["id"]!!, force = true).getOrThrow()
                redirectSmart("/containers?notice=removed")
            }
        }

        get("/{id}/logs") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            logger.info { "Streaming logs for container: $id" }
            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                withHeartbeat { send ->
                    try {
                        val running = dockerClient.containers.getInfo(id).getOrThrow().state?.running == true
                        val logsFlow = dockerClient.containers.getLogs(
                            id = id,
                            parameters = ContainerLogsParameters(follow = running, stdout = true, stderr = true, tail = "200")
                        ).getOrThrow()

                        logsFlow.collect { log ->
                            val channel = if (log.type == LogLine.Type.STDERR) "stderr" else "stdout"
                            val html =
                                "<div data-channel='$channel'><span>${log.line.escapeHtml().replace("\n", "<br>")}</span></div>"
                            send("data: $html\n\n")
                        }
                        send("event: done\ndata: end\n\n")
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        send("event: failure\ndata: Log stream disconnected. Reconnect to retry.\n\n")
                    }
                }
            }
        }

        get("/{id}/stats") {
            val id = call.parameters["id"]!!
            call.response.cacheControl(CacheControl.NoCache(null))
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                withHeartbeat { send ->
                    dockerClient.containers.getStats(id).getOrThrow().collect { stats ->
                        val memory = ui.bytes(stats.memoryStats?.usage)
                        val limit = ui.bytes(stats.memoryStats?.limit)
                        val used = stats.memoryStats?.usage
                        val total = stats.memoryStats?.limit
                        val percent = if (used != null && total != null && total > 0uL) " (${(used.toDouble() / total.toDouble() * 1000).toLong() / 10.0}%)" else ""
                        send("data: Memory: $memory / $limit$percent<br>Updated: ${stats.read.toString().escapeHtml()}\n\n")
                    }
                    send("event: done\ndata: end\n\n")
                }
            }
        }

        post("/{id}/exec") {
            containerAction {
            val id = call.parameters["id"]!!
            val command = call.receiveParameters()["command"]?.trim()?.takeIf { it.isNotEmpty() }
            val exec = dockerClient.containers.execCreate(
                id, ExecConfig(
                    attachStdin = true,
                    attachStdout = true,
                    attachStderr = true,
                    cmd = command?.split(" ") ?: listOf("/bin/sh"),
                    tty = true
                )
            ).getOrThrow()
            call.response.headers.append("HX-Redirect", "/exec/${exec.id}")
            call.respond(HttpStatusCode.OK)
            }
        }
    }
}
