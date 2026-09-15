package routes.containers

import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import dev.limebeck.libs.docker.client.model.ContainerSummary
import io.ktor.http.Parameters
import kotlinx.html.*
import ui.*
import routes.images.renderPullForm

fun FlowContent.containerTable(containers: List<ContainerSummary>) {
    div { attributes["data-resource-list"] = ""
        if (containers.isEmpty()) {
            emptyState("No containers yet", "Create a container from an image to get started.", "Create container", "/containers/create")
        } else {
            filterToolbar(containers.mapNotNull { it.state?.value }.distinct().sorted())
            div("table-wrap") {
                attributes["role"] = "region"; attributes["aria-label"] = "Containers"; attributes["tabindex"] = "0"
                table("resource-table") {
                    thead { tr { listOf("Name", "Image", "State", "Actions").forEach { th { scope = ThScope.col; +it } } } }
                    tbody {
                        containers.forEach { container ->
                            val id = container.id.orEmpty()
                            val name = container.names?.joinToString(", ") { it.removePrefix("/") }.orEmpty().ifEmpty { id.take(12) }
                            val state = container.state?.value ?: "unknown"
                            tr {
                                attributes["data-name"] = name.lowercase(); attributes["data-state"] = state
                                attributes["data-search-text"] = "$name $id ${container.image.orEmpty()}".lowercase()
                                td { attributes["data-label"] = "Name"; pageLink(name, "/containers/$id", "resource-name"); code("resource-id") { +id.take(12) } }
                                td { attributes["data-label"] = "Image"; code("image-name") { +(container.image ?: "Unknown image") } }
                                td { attributes["data-label"] = "State"; stateBadge(state); container.status?.let { small("resource-id") { +it } } }
                                td { attributes["data-label"] = "Actions"; div("actions") {
                                    if (state == "running") actionButton("Stop", "/containers/$id/stop")
                                    else if (state in listOf("exited", "created")) actionButton("Start", "/containers/$id/start")
                                    details("action-menu") {
                                        summary("btn btn-small") { attributes["aria-label"] = "More actions for $name"; +"More" }
                                        div("menu-content") {
                                            pageLink("Logs", "/containers/$id?tab=logs", "btn btn-small")
                                            actionButton("Delete", "/containers/$id", "delete", "Delete $name? A running container will be forcibly stopped. Its writable layer is removed; named volumes are retained.", danger = true)
                                        }
                                    }
                                } }
                            }
                        }
                    }
                }
            }
            tableFoot()
        }
    }
}

fun FlowContent.renderContainerDetailsPage(id: String, info: ContainerInspectResponse?, pendingReplacement: Boolean = false, tab: String = "overview") {
    breadcrumb("Containers", "/containers")
    if (info == null) { pageHeading("Container unavailable"); renderError("Could not inspect this container. Return to the list and retry."); return }
    val name = info.name?.removePrefix("/") ?: id.take(12)
    val state = info.state?.status?.value ?: "unknown"
    pageHeading(name, info.config?.image ?: info.image ?: "Unknown image") {
        if (info.state?.running == true) actionButton("Stop", "/containers/$id/stop")
        else if (state in listOf("exited", "created")) actionButton("Start", "/containers/$id/start")
        details("action-menu") { summary("btn") { +"More actions" }; div("menu-content") {
            if (info.config?.labels?.get(MANAGED_LABEL) == "true" && !pendingReplacement) pageLink("Recreate", "/containers/$id/recreate", "btn btn-small")
            actionButton("Delete container", "/containers/$id", "delete", "Delete $name? This forcibly stops a running container and removes its writable layer. Named volumes are retained.", danger = true)
        } }
    }
    div("actions") {
        val failed = state == "exited" && info.state?.exitCode?.let { it != 0 } == true
        stateBadge(if (failed) "error" else state, if (failed) "Exited (${info.state?.exitCode})" else state.replaceFirstChar(Char::uppercase))
        code("muted") { +id.take(12) }; badge(info.platform ?: "Platform unknown")
    }
    if (pendingReplacement) {
        div("panel stack") {
            h2 { +"Replacement needs verification" }
            p { +"Check application readiness and retained data before confirming. Running alone does not mean ready." }
            infoRow("Previous container", info.config?.labels?.get(PREVIOUS_LABEL).orEmpty(), true)
            p("hint") { +"The previous container is stopped. Rollback restores its configuration, but cannot undo writes to shared volumes." }
            div("actions") {
                actionButton("Confirm replacement", "/containers/$id/confirm", confirm = "Readiness and retained data verified? Delete the previous container and retain named volumes?")
                actionButton("Roll back", "/containers/$id/rollback", confirm = "Remove this replacement and restore the previous container? Writes to shared volumes are not undone.")
            }
        }
    }
    nav("tabs") {
        attributes["aria-label"] = "Container sections"
        listOf("overview", "logs", "terminal", "configuration").forEach { item ->
            a(href = "/containers/$id?tab=$item") {
                attributes["hx-get"] = href; attributes["hx-target"] = "#main-content"; attributes["hx-push-url"] = "true"
                if (item == tab) attributes["aria-current"] = "page"
                +item.replaceFirstChar(Char::uppercase)
            }
        }
    }
    when (tab) {
        "logs" -> renderLogsWindow(id)
        "terminal" -> if (info.state?.running == true) {
            card("stack") {
                h2 { +"Open a terminal" }; p("muted") { +"Start a new interactive exec session in this container." }
                form(classes = "stack") {
                    attributes["hx-post"] = "/containers/$id/exec"; attributes["hx-target"] = "#main-content"; attributes["hx-sync"] = "this:drop"
                    label("field") { +"Command"; input(type = InputType.text, name = "command") { value = "/bin/sh"; attributes["aria-describedby"] = "command-hint" } }
                    p("hint") { this.id = "command-hint"; +"Space-separated arguments, without shell quote expansion. Empty opens /bin/sh." }
                    div("actions") { button(type = ButtonType.submit, classes = "btn btn-primary") { +"Open terminal" } }
                }
                p("hint") { +"Closing the connection ends this session. Opening again creates a new session." }
            }
        } else emptyState("Container is not running", "Start the container before opening a terminal. Its previous logs remain available.")
        "configuration" -> div("grid") {
            infoCard("Command") { infoRow("Entrypoint and arguments", (listOfNotNull(info.path) + info.args.orEmpty()).joinToString(" "), true); infoRow("Driver", info.driver ?: "Unavailable") }
            infoCard("Environment") {
                if (info.config?.env.isNullOrEmpty()) p("muted") { +"No environment variables" }
                info.config?.env?.forEach { value -> infoRow(value.substringBefore('='), value.substringAfter('=', ""), true) }
            }
        }
        else -> div("stack") {
            div("grid") {
                infoCard("Container") { infoRow("Full ID", id, true); infoRow("Created", info.created ?: "Unavailable"); infoRow("Restarts", info.restartCount?.toString() ?: "Unavailable") }
                infoCard("Published ports") {
                    if (info.networkSettings?.ports.isNullOrEmpty()) p("muted") { +"No ports published" }
                    info.networkSettings?.ports?.forEach { (port, bindings) -> infoRow(port, bindings?.joinToString { "${it.hostIp}:${it.hostPort}" }.orEmpty().ifEmpty { "Not mapped" }, true) }
                }
                infoCard("Volumes") {
                    if (info.mounts.isNullOrEmpty()) p("muted") { +"No volumes mounted" }
                    info.mounts?.forEach { infoRow(it.destination ?: "Unknown destination", it.name ?: it.source ?: "Anonymous", true) }
                }
                infoCard("Networks") {
                    if (info.networkSettings?.networks.isNullOrEmpty()) p("muted") { +"No networks attached" }
                    info.networkSettings?.networks?.keys?.forEach { p { +it } }
                }
            }
            if (info.state?.running == true) renderLiveStream("/containers/$id/stats", "stats-view", append = false, title = "Memory")
        }
    }
}

fun FlowContent.renderLogsWindow(containerId: String) { renderLiveStream("/containers/$containerId/logs", "logs-view", title = "Container logs") }

fun FlowContent.renderCreateForm(info: ContainerInspectResponse? = null, action: String = "/containers/create", submitted: Parameters? = null, error: String? = null, errorField: String? = null) {
    val replacing = action != "/containers/create"
    val back = if (replacing) action.removeSuffix("/recreate") else "/containers"
    fun value(name: String, fallback: String) = if (submitted != null) submitted[name].orEmpty() else fallback
    div("form-page") {
        breadcrumb(if (replacing) "Container" else "Containers", back)
        pageHeading(if (replacing) "Prepare replacement" else "Create container", if (replacing) "Review the configuration before replacing the current container." else "Configure a new container on your local Docker host.")
        div { id = "config-form-region"
            if (error != null) renderError(error)
            form(classes = "stack") {
                method = FormMethod.post; this.action = action; attributes["hx-post"] = action; attributes["hx-target"] = "#main-content"; attributes["hx-sync"] = "this:drop"; attributes["data-config-form"] = ""
                attributes["hx-history"] = "false"
                fun FlowContent.field(name: String, title: String, content: String, hint: String = "", multiline: Boolean = false, repeat: Boolean = false) {
                    div {
                        if (repeat) {
                            attributes["data-repeat"] = name
                            h3 { +title }
                            div("repeat-fields") {
                                value(name, content).split('\n').ifEmpty { listOf("") }.forEach { line ->
                                    div("repeat-row") {
                                        input(type = InputType.text, name = "${name}-row") { this.value = line; attributes["aria-label"] = "$title entry"; attributes["aria-describedby"] = "hint-$name"; if (name == errorField) attributes["aria-invalid"] = "true" }
                                        button(type = ButtonType.button, classes = "btn btn-small") { attributes["data-remove-row"] = ""; attributes["aria-label"] = "Remove $title entry"; +"Remove" }
                                    }
                                }
                            }
                            button(type = ButtonType.button, classes = "btn btn-small") { attributes["data-add-row"] = ""; +"Add entry" }

                        } else label("field") {
                            +title
                            if (multiline) textArea { id = "config-$name"; this.name = name; rows = "3"; attributes["aria-describedby"] = "hint-$name"; if (name == errorField) attributes["aria-invalid"] = "true"; +value(name, content) }
                            else input(type = InputType.text, name = name) { id = "config-$name"; this.value = value(name, content); required = name == "image"; attributes["aria-describedby"] = "hint-$name"; if (name == errorField) attributes["aria-invalid"] = "true" }
                        }
                        p("hint") { id = "hint-$name"; +hint }
                        if (name == errorField && error != null) p("field-error") { +error }
                    }
                }
                card("form-section") {
                    h2 { +"Basics" }
                    if (!replacing) field("name", "Name (optional)", "", "Leave empty to let Docker choose a name.")
                    field("image", "Image", info?.config?.image.orEmpty(), "Use an image already available on this host. Pull a missing image below without leaving this form.")
                    field("cmd", "Command arguments", info?.config?.cmd?.joinToString("\n") ?: "/bin/sh", "One argument per line. Empty uses the image default.", multiline = true)
                    label("check") { input(type = InputType.checkBox, name = "tty") { checked = if (submitted != null) submitted["tty"] == "on" else info?.config?.tty ?: true }; +"Interactive TTY" }
                }
                details("panel section-disclosure") {
                    open = replacing || submitted != null
                    summary { +"Environment, ports, volumes and network" }
                    div("stack") {
                        field("env", "Environment", info?.config?.env?.joinToString("\n").orEmpty(), "KEY=value. Values stay in this page and are not saved to browser storage.", repeat = true)
                        field("ports", "Published ports", info?.hostConfig?.portBindings.orEmpty().flatMap { (port, bindings) -> bindings.orEmpty().map { "${it.hostIp}:${it.hostPort}:$port" } }.joinToString("\n"), "127.0.0.1:host-port:container-port/tcp (or /udp). Host port 0 chooses an available port.", repeat = true)
                        field("volumes", "Named volumes", info?.mounts.orEmpty().filter { it.type?.value == "volume" }.joinToString("\n") { "${it.name}:${it.destination}" }, "volume-name:/container/path. Named volumes survive container deletion; missing volumes are created.", repeat = true)
                        field("network", "Existing network (optional)", info?.networkSettings?.networks?.keys?.singleOrNull()?.takeUnless { it == "bridge" }.orEmpty(), "Leave empty for the Docker default network.")
                    }
                }
                if (replacing) div("notice notice-warning") { +"The previous container is stopped only after preparation succeeds. Verify readiness before confirming. Shared-volume writes cannot be rolled back automatically." }
                div("form-footer") { pageLink("Cancel", back, "btn"); button(type = ButtonType.submit, classes = "btn btn-primary") { +(if (replacing) "Prepare replacement" else "Create and run") } }
            }
        }
        details("panel section-disclosure") { summary { +"Need to pull an image first?" }; renderPullForm(embedded = true) }
    }
}
