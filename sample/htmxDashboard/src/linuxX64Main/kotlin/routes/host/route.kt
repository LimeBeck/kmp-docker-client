package routes.host

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.api.system
import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.html.*
import routes.pageAction
import routes.respondSmart
import ui.*

private fun ContainerInspectResponse.status() = state?.status?.value ?: "unknown"
private fun ContainerInspectResponse.unhealthy() = state?.health?.status?.value == "unhealthy"
private fun ContainerInspectResponse.failed() = status() == "exited" && (state?.exitCode ?: 0) != 0
private fun ContainerInspectResponse.severity() = when {
    unhealthy() -> 0
    status() == "restarting" -> 1
    status() == "dead" -> 2
    failed() -> 3
    else -> 4
}

fun Routing.hostRoute(client: DockerClient) {
    get("/host") {
        pageAction("Host") {
            val listed = client.containers.getList(all = true).getOrThrow()
            val containers = listed.map { client.containers.getInfo(requireNotNull(it.id)).getOrThrow() }
            val projects = containers.filter { !it.config?.labels?.get("com.docker.compose.project").isNullOrBlank() }
                .groupBy { it.config!!.labels!!["com.docker.compose.project"]!! }.toList().sortedBy { it.first }
            val attention = containers.filter { it.severity() < 4 }
                .sortedWith(compareBy<ContainerInspectResponse> { it.severity() }.thenByDescending { it.state?.finishedAt.orEmpty() })
            respondSmart("Host") {
                div("host-overview") {
                    pageHeading("Host overview", "What needs attention, then what is running. Running does not imply healthy.") {
                        pageLink("↻ Refresh", "/host", "btn")
                    }
                    div("host-metrics") {
                        hostMetric("Running", containers.count { it.status() == "running" }, "of ${containers.size} containers", "good")
                        hostMetric("Unhealthy", containers.count { it.unhealthy() }, "Reported health checks", "bad")
                        hostMetric("Exited nonzero", containers.count { it.failed() }, "Exited with an error code", "warn")
                        hostMetric("Stopped", containers.count { it.status() in setOf("created", "exited", "dead") }, "Created, exited or dead", "muted")
                    }
                    div("host-columns") {
                        section("panel host-attention") {
                            div("host-panel-heading") { h2 { +"Needs attention" }; pageLink("All containers →", "/containers") }
                            if (attention.isEmpty()) p("muted") { +"No unhealthy, restarting, dead or failed containers observed." }
                            attention.take(12).forEach { container ->
                                val id = requireNotNull(container.id).encodeURLPathPart()
                                div("host-attention-row") {
                                    div { pageLink(container.name?.removePrefix("/") ?: id.take(12), "/containers/$id", "resource-name"); code("resource-id") { +id.take(12) } }
                                    span("host-issue issue-${container.severity()}") {
                                        +(when { container.unhealthy() -> "Unhealthy"; container.failed() -> "Exit ${container.state?.exitCode}"; else -> container.status() })
                                    }
                                    pageLink("Logs", "/containers/$id?tab=logs", "btn btn-small")
                                }
                            }
                            p("hint") { +"${attention.size} containers need attention. Showing up to 12, ordered by severity, then last finish time. Snapshot · refresh to update." }
                        }
                        div("host-right") {
                            section("panel") {
                                div("host-panel-heading") { h2 { +"Compose projects" }; pageLink("All projects →", "/compose") }
                                if (projects.isEmpty()) p("muted") { +"No container-backed Compose projects found." }
                                projects.forEach { (name, members) ->
                                    div("host-project") {
                                        div("host-project-heading") { pageLink(name, "/compose/project?project=${name.encodeURLQueryComponent()}"); small { +"${members.count { it.status() == "running" }} / ${members.size} running" } }
                                        div("host-project-bar") { attributes["aria-label"] = "Observed containers for $name"
                                            members.forEach { member -> span("segment-${if (member.unhealthy()) "bad" else if (member.status() == "running") "good" else "stopped"}") { attributes["title"] = "${member.name?.removePrefix("/")}: ${member.status()}${if (member.unhealthy()) ", unhealthy" else ""}" } }
                                        }
                                    }
                                }
                                p("hint") { +"One segment per observed container, including one-off containers." }
                            }
                            renderLiveStream("/system/events", "host-events", title = "Live events")
                        }
                    }
                }
            }
        }
    }
    get("/host/summary") {
        call.response.headers.append("Cache-Control", "no-store")
        try {
            val info = client.system.getInfo().getOrThrow()
            call.respondHtml { body {
                span("status status-running") { +"Engine ${info.serverVersion ?: "unknown"} · available at load" }
                small { +"${info.operatingSystem ?: "Unknown OS"} · ${info.architecture ?: "unknown"}" }
                small { +"${bytes(info.memTotal?.toULong())} · ${info.NCPU?.toString() ?: "?"} CPU" }
            } }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { call.respondHtml { body { span("status status-error") { +"Engine unavailable" }; small { +"Reload the page to retry" } } } }
    }
}

private fun FlowContent.hostMetric(label: String, count: Int, hint: String, tone: String) {
    div("panel host-metric tone-$tone") { small { +label }; strong { +count.toString() }; p { +hint } }
}
