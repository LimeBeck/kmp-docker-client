package routes.compose

import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import dev.limebeck.libs.docker.client.model.Network
import dev.limebeck.libs.docker.client.model.Volume
import dev.limebeck.libs.docker.compose.ComposeContainer
import dev.limebeck.libs.docker.compose.ComposeService
import io.ktor.http.encodeURLPathPart
import kotlinx.html.*
import ui.*

internal fun FlowContent.composeProjectHeader(
    name: String, service: String?, base: String, serviceCount: Int,
    containers: List<ComposeContainer>, inspections: Map<String, ContainerInspectResponse>,
) {
    val directories = inspections.values.mapNotNull { it.config?.labels?.get("com.docker.compose.project.working_dir") }.distinct()
    div("compose-heading") {
        div {
            div("compose-title") { h1 { +(if (service == null) name else "$name / $service") }; span("compose-badge") { +"Compose" } }
            div("compose-location") {
                val directory = directories.singleOrNull()
                if (directory != null) { span { +directory }; copyButton(directory, "Copy project directory") }
                else span { +"${containers.size} observed containers on Local Docker" }
            }
        }
        div("actions") {
            pageLink("↻ Refresh", base, "btn")
        }
    }
    div("compose-metrics") {
        metric(serviceCount, "services", "Discovered on this host", "services")
        metric(containers.count { it.state?.status?.value == "running" }, "running", "Containers currently running", "running")
        metric(containers.count { it.state?.health?.status?.value == "unhealthy" }, "unhealthy", "Container health checks", "unhealthy")
        metric(containers.count { it.state?.status?.value in setOf("created", "exited", "dead") }, "stopped", "Created, exited or dead containers", "stopped")
    }
}

private fun FlowContent.metric(count: Int, label: String, hint: String, tone: String) {
    div("compose-metric metric-$tone") {
        span("metric-symbol") { if (tone == "services") icon("containers") else +"●" }
        div { strong { +count.toString() }; span("metric-label") { +label }; small { +hint } }
    }
}

internal fun FlowContent.composeProjectTabs(base: String, active: String, services: Int, containers: Int, volumes: Int, networks: Int) {
    nav("compose-tabs") {
        attributes["aria-label"] = "Project sections"
        listOf("services" to services, "containers" to containers, "volumes" to volumes, "networks" to networks, "logs" to null).forEach { (tab, count) ->
            a(href = "$base&tab=$tab", classes = if (active == tab) "active" else "") {
                attributes["hx-get"] = "$base&tab=$tab"; attributes["hx-target"] = "#main-content"; attributes["hx-push-url"] = "true"
                if (active == tab) attributes["aria-current"] = "page"
                +tab.replaceFirstChar(Char::uppercase); if (count != null) span { +count.toString() }
            }
        }
    }
}

private fun serviceState(containers: List<ComposeContainer>): String {
    val values = containers.map { it.state?.status?.value ?: "unknown" }.distinct()
    return if (values.size == 1) values.single() else "mixed"
}

internal fun FlowContent.composeServiceTable(project: String, services: List<ComposeService>, inspections: Map<String, ContainerInspectResponse>) {
    div("compose-toolbar") {
        label("field") { span("sr-only") { +"Search services" }; input(type = InputType.search) { attributes["data-search"] = ""; placeholder = "Search services…" } }
        label("field") { span("sr-only") { +"Service state" }; select { attributes["data-filter"] = ""; option { value = ""; +"All statuses" }; listOf("running", "exited", "created", "paused", "restarting", "mixed", "dead", "unknown").forEach { option { value = it; +it.replaceFirstChar(Char::uppercase) } } } }
        label("field") { span("sr-only") { +"Sort services" }; select { attributes["data-sort"] = ""; option { value = "name"; +"Service ↑" }; option { value = "state"; +"State ↑" } } }
    }
    div("table-wrap compose-service-table") { table {
        thead { tr { listOf("Service", "Container", "Replicas", "State", "Health", "Ports", "Actions").forEach { th { scope = ThScope.col; +it } } } }
        tbody { services.forEach { service ->
            val members = service.containers
            val replicas = service.replicas
            val state = serviceState(if (replicas.isNotEmpty()) replicas else members)
            val primary = replicas.firstOrNull() ?: members.firstOrNull()
            val images = members.mapNotNull { it.image }.distinct()
            val health = members.mapNotNull { it.state?.health?.status?.value }.distinct()
            val ports = members.flatMap { member ->
                val info = inspections[member.id]
                info?.networkSettings?.ports?.keys.orEmpty() +
                    info?.hostConfig?.portBindings?.keys.orEmpty() + info?.config?.exposedPorts?.keys.orEmpty()
            }.distinct().sorted()
            tr {
                attributes["data-name"] = service.name.lowercase(); attributes["data-state"] = state
                attributes["data-search-text"] = (listOf(service.name) + images + members.map { "${it.name} ${it.id}" }).joinToString(" ").lowercase()
                td { attributes["data-label"] = "Service"
                    div("compose-service-name") {
                        span("service-avatar avatar-${avatarTone(images.firstOrNull().orEmpty())}") { +service.name.take(2).uppercase() }
                        div { pageLink(service.name, servicePath(project, service.name), "resource-name"); span("resource-id") { +images.joinToString(", ").ifBlank { "Image unavailable" } } }
                    }
                }
                td { attributes["data-label"] = "Container"
                    if (primary != null) {
                        pageLink(primary.name ?: primary.id.take(12), "/containers/${primary.id.encodeURLPathPart()}", "compose-container-link")
                        div("compose-id") { code { +primary.id.take(12) }; copyButton(primary.id, "Copy container ID for ${service.name}") }
                    }
                    if (members.size > 1) details("compose-replicas") {
                        summary { +"All ${members.size} containers" }
                        members.forEach { member -> div { pageLink(member.name ?: member.id.take(12), "/containers/${member.id.encodeURLPathPart()}"); small { +" · ${member.state?.status?.value ?: "unknown"}${if (member.oneOff == true) " · One-off" else ""}" } } }
                    }
                }
                td { attributes["data-label"] = "Replicas"; span { attributes["title"] = "Running / observed regular replicas"; +"${replicas.count { it.state?.status?.value == "running" }} / ${replicas.size}" } }
                td { attributes["data-label"] = "State"; stateBadge(state, if (state == "exited") "Stopped" else state.replaceFirstChar(Char::uppercase)) }
                td { attributes["data-label"] = "Health"
                    val result = when { "unhealthy" in health -> "unhealthy"; health.isEmpty() || health.all { it == "none" } -> "none"; health.size == 1 -> health.single(); else -> "mixed" }
                    span("compose-health health-$result") { +(if (result == "none") "No healthcheck reported" else result.replaceFirstChar(Char::uppercase)) }
                }
                td { attributes["data-label"] = "Ports"; attributes["title"] = "Configured or exposed container ports"; +ports.joinToString(", ").ifBlank { "—" } }
                td { attributes["data-label"] = "Actions"; div("actions") {
                    pageLink("Logs", servicePath(project, service.name) + "&tab=logs", "btn btn-small")
                    actionMenu("⋮", "Actions for ${service.name}") { pageLink("Service details", servicePath(project, service.name)); pageLink("Live logs", servicePath(project, service.name) + "&tab=logs&mode=live") }
                } }
            }
        } }
    } }
    tableFoot()
}

private fun avatarTone(image: String) = when {
    "postgres" in image -> "blue"; "redis" in image -> "red"; "python" in image -> "yellow"; "qdrant" in image -> "purple"; else -> "cyan"
}
private fun FlowContent.copyButton(value: String, label: String) {
    button(type = ButtonType.button, classes = "compose-copy") { attributes["data-copy"] = value; attributes["aria-label"] = label; attributes["title"] = label; +"⧉" }
}
internal fun FlowContent.composeVolumeTable(volumes: List<Volume>) {
    if (volumes.isEmpty()) { emptyState("No project volumes", "No volumes with this Compose project label were found."); return }
    div("table-wrap") { table { thead { tr { listOf("Volume", "Driver", "Mountpoint").forEach { th { +it } } } }; tbody { volumes.forEach { v -> tr {
        td { attributes["data-label"] = "Volume"; +v.name }; td { attributes["data-label"] = "Driver"; +v.driver }; td { attributes["data-label"] = "Mountpoint"; code { +v.mountpoint } }
    } } } } }
}
internal fun FlowContent.composeNetworkTable(networks: List<Network>) {
    if (networks.isEmpty()) { emptyState("No project networks", "No networks with this Compose project label were found."); return }
    div("table-wrap") { table { thead { tr { listOf("Network", "Driver", "Scope").forEach { th { +it } } } }; tbody { networks.forEach { n -> tr {
        td { attributes["data-label"] = "Network"; +(n.name ?: "Unnamed"); code("resource-id") { +n.id.orEmpty().take(12) } }; td { attributes["data-label"] = "Driver"; +n.driver.orEmpty() }; td { attributes["data-label"] = "Scope"; +n.scope.orEmpty() }
    } } } } }
}
