package routes.compose

import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.api.volumes
import dev.limebeck.libs.docker.client.api.networks
import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.ContainerLogsParameters
import dev.limebeck.libs.docker.compose.ComposeContainer
import dev.limebeck.libs.docker.compose.compose
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.html.*
import routes.pageAction
import routes.respondSmart
import routes.withHeartbeat
import ui.*

internal fun String.queryValue() = encodeURLQueryComponent()
internal fun projectPath(project: String) = "/compose/project?project=${project.queryValue()}"
internal fun servicePath(project: String, service: String) = "/compose/service?project=${project.queryValue()}&service=${service.queryValue()}"

fun Routing.composeRoute(client: DockerClient) {
    get("/compose") {
        pageAction("Compose") {
            val snapshot = client.compose.discover().getOrThrow()
            respondSmart("Compose") {
                pageHeading("Compose", "${snapshot.projects.size} projects on this host") { pageLink("Refresh", "/compose", "btn") }
                p { +"Existing containers grouped by project and service. Projects without containers are not shown." }
                if (snapshot.projects.isEmpty()) emptyState("No Compose projects", "Create a project with Docker Compose to see its containers here.")
                else {
                    filterToolbar()
                    div("table-wrap") { table {
                        thead { tr { listOf("Project", "Services", "Containers", "Running").forEach { th { +it } } } }
                        tbody { snapshot.projects.forEach { project ->
                            val containers = project.services.flatMap { it.containers } + project.unassignedContainers
                            tr {
                                attributes["data-name"] = project.name.lowercase(); attributes["data-search-text"] = project.name.lowercase()
                                td { attributes["data-label"] = "Project"; pageLink(project.name, projectPath(project.name)) }
                                td { attributes["data-label"] = "Services"; +project.services.size.toString() }
                                td { attributes["data-label"] = "Containers"; +containers.size.toString() }
                                td { attributes["data-label"] = "Running"; +containers.count { it.state?.status?.value == "running" }.toString() }
                            }
                        } }
                    } }
                    tableFoot()
                }
                if (snapshot.unassignedContainers.isNotEmpty()) {
                    h2 { +"Containers without a project label" }
                    composeContainers(snapshot.unassignedContainers)
                }
            }
        }
    }
    listOf("/compose/project", "/compose/service").forEach { path ->
        get(path) {
            val query = call.request.queryParameters
            val name = query["project"]?.takeIf { it.isNotBlank() } ?: return@get call.respond(HttpStatusCode.BadRequest)
            val service = if (path.endsWith("/service")) query["service"]?.takeIf { it.isNotBlank() }
                ?: return@get call.respond(HttpStatusCode.BadRequest) else null
            pageAction("Compose project") {
                val project = client.compose.getProject(name).getOrThrow()
                if (project == null || service != null && project.services.none { it.name == service }) {
                    respondSmart("Compose not found", HttpStatusCode.NotFound) {
                        breadcrumb("Compose", "/compose"); emptyState("Not found", "This project or service no longer has discoverable containers.")
                    }
                    return@pageAction
                }
                val selected = if (query["selection"] == "selected") query.getAll("services").orEmpty().toSet()
                    else service?.let { setOf(it) }
                val live = query["mode"] == "live"
                val available = if (service == null) project.services else project.services.filter { it.name == service }
                require(selected == null || selected.all { candidate -> available.any { it.name == candidate } }) { "Unknown service selection. Refresh the project." }
                val base = if (service == null) projectPath(name) else servicePath(name, service)
                val streamPath = "/compose/logs?project=${name.queryValue()}&mode=${if (live) "live" else "history"}" +
                    (selected?.joinToString("", prefix = "&selection=selected") { "&services=${it.queryValue()}" } ?: "")
                val activeTab = query["tab"]?.takeIf { it in setOf("services", "containers", "volumes", "networks", "logs") }
                    ?: if (query["selection"] != null || query["mode"] != null) "logs" else "services"
                val members = available.flatMap { it.containers } + if (service == null) project.unassignedContainers else emptyList()
                val inspections = members.associate { it.id to client.containers.getInfo(it.id).getOrThrow() }
                val filters = mapOf("label" to listOf("com.docker.compose.project=$name"))
                val volumes = client.volumes.getList(filters = filters).getOrThrow().volumes.orEmpty()
                val networks = client.networks.list(filters = filters).getOrThrow()
                respondSmart(if (service == null) name else "$name / $service") {
                  div("compose-workspace") {
                    breadcrumb(if (service == null) "Compose" else name, if (service == null) "/compose" else projectPath(name))
                    composeProjectHeader(name, service, base, available.size, members, inspections)
                    composeProjectTabs(base, activeTab, available.size, members.size, volumes.size, networks.size)
                    when (activeTab) {
                        "services" -> composeServiceTable(name, available, inspections)
                        "containers" -> {
                            composeContainers(members)
                            if (service == null && project.unassignedContainers.isNotEmpty()) p("hint") { +"Includes containers without a service label." }
                        }
                        "volumes" -> composeVolumeTable(volumes)
                        "networks" -> composeNetworkTable(networks)
                    }
                    if (activeTab == "logs") {
                    card {
                        h2 { +"Project logs" }
                        form(action = path, method = FormMethod.get, classes = "compose-filters") {
                            attributes["hx-get"] = path; attributes["hx-target"] = "#main-content"; attributes["hx-push-url"] = "true"
                            input(type = InputType.hidden, name = "project") { value = name }
                            if (service != null) input(type = InputType.hidden, name = "service") { value = service }
                            input(type = InputType.hidden, name = "tab") { value = "logs" }
                            input(type = InputType.hidden, name = "selection") { value = "selected" }
                            fieldSet {
                                legend { +"Services" }
                                div("actions") { available.forEach { item ->
                                    label { input(type = InputType.checkBox, name = "services") { value = item.name; checked = selected == null || item.name in selected }; +" ${item.name}" }
                                } }
                            }
                            label("field") { +"Mode"; select { this.name = "mode"; option { value = "history"; this.selected = !live; +"History" }; option { value = "live"; this.selected = live; +"Live" } } }
                            div("actions") {
                                button(type = ButtonType.submit, classes = "btn btn-primary") { +"Apply" }
                                pageLink(if (service == null) "All project containers" else "Reset", "$base&tab=logs", "btn")
                            }
                        }
                        p { +"200 history records per container; the view keeps 200 records total. Reconnect to discover new replicas. Clear all services to stop reading." }
                    }
                    if (selected?.isEmpty() == true) emptyState("No services selected", "Select one or more services and apply to read logs.")
                    else renderLiveStream(streamPath, "compose-logs", title = if (live) "Live logs" else "Log history")
                    } else {
                        div("compose-tip") { span { icon("info") }; div { strong { +"Quick tips" }; p { +"Open a service to inspect its replicas, or use Logs to follow several services together." } } }
                    }
                  }
                }
            }
        }
    }
    get("/compose/logs") {
        val query = call.request.queryParameters
        val project = query["project"]?.takeIf { it.isNotBlank() } ?: return@get call.respond(HttpStatusCode.BadRequest)
        val services = if (query["selection"] == "selected") query.getAll("services").orEmpty().toSet() else null
        if (services?.any { it.isBlank() } == true) return@get call.respond(HttpStatusCode.BadRequest)
        call.response.cacheControl(CacheControl.NoCache(null))
        call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
            withHeartbeat { send ->
                try {
                    client.compose.logs(project, services, ContainerLogsParameters(follow = query["mode"] == "live", tail = "200")).collect { record ->
                        val channel = record.log.type.name.lowercase()
                        val identity = "${record.service ?: "Unassigned"} / ${record.containerName ?: record.containerId.take(12)} [$channel]"
                        val html = "<div data-channel='$channel'><strong>${identity.escapeHtml().replace("\r", "").replace("\n", " ")}</strong> <span>${record.log.line.escapeHtml().replace("\r", "").replace("\n", "<br>")}</span></div>"
                        send("data: $html\n\n")
                    }
                    send("event: done\ndata: end\n\n")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { send("event: failure\ndata: Could not read Compose logs. Select fewer services (maximum 64 containers) or reconnect.\n\n") }
            }
        }
    }
}

internal fun FlowContent.composeContainers(containers: List<ComposeContainer>) {
    div("table-wrap") { table {
        thead { tr { listOf("Container", "Replica", "State", "Health", "Kind").forEach { th { +it } } } }
        tbody { containers.forEach { container -> tr {
            td { attributes["data-label"] = "Container"; pageLink(container.name ?: container.id.take(12), "/containers/${container.id.encodeURLPathPart()}"); code("resource-id") { +container.id.take(12) } }
            td { attributes["data-label"] = "Replica"; +(container.replicaNumber?.toString() ?: "Unknown") }
            td { attributes["data-label"] = "State"; stateBadge(container.state?.status?.value ?: "unknown") }
            td { attributes["data-label"] = "Health"; +(container.state?.health?.status?.value ?: "Not reported") }
            td { attributes["data-label"] = "Kind"; +(when (container.oneOff) { true -> "One-off"; false -> "Regular"; null -> "Unknown" }) }
        } } }
    } }
}
