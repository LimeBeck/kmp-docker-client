package routes.containers

import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import dev.limebeck.libs.docker.client.model.ContainerSummary
import kotlinx.html.*
import ui.badge
import ui.card
import ui.infoCard
import ui.infoRow
import ui.renderLiveStream


fun FlowContent.containerTable(containers: List<ContainerSummary>) {
    div("bg-gray-800 rounded-lg shadow-lg overflow-x-auto border border-gray-700") {
        attributes["role"] = "region"
        attributes["aria-label"] = "Containers"
        attributes["tabindex"] = "0"
        table("w-full min-w-[48rem] table-fixed text-left") {
            thead("bg-gray-700 text-gray-400 uppercase text-xs") {
                tr {
                    listOf("ID" to "w-36", "Name" to "w-1/4", "Image" to "", "State" to "w-28", "Actions" to "w-48").forEach { (label, width) ->
                        th(classes = "px-4 py-3 $width") { +label }
                    }
                }
            }
            tbody("divide-y divide-gray-700") {
                containers.forEach { container ->
                    tr("hover:bg-gray-700/50 transition-colors") {
                        td("px-4 py-4 font-mono text-sm") { +(container.id?.take(12) ?: "-") }
                        td("px-4 py-4 font-mono text-sm [overflow-wrap:anywhere]") {
                            +(container.names?.joinToString(", ") { it.removePrefix("/") } ?: "-")
                        }
                        td("px-4 py-4 [overflow-wrap:anywhere]") { +(container.image ?: "-") }
                        td("px-4 py-4") {
                            val dotColor =
                                if (container.state == ContainerSummary.State.RUNNING) "bg-green-400" else "bg-red-400"
                            div("flex items-center gap-2") {
                                div("w-2 h-2 shrink-0 rounded-full $dotColor") {}
                                +(container.state?.value ?: "unknown")
                            }
                        }
                        td("px-4 py-4") {
                            div("flex flex-wrap gap-x-3 gap-y-2 text-sm") {
                                button(classes = "text-blue-400 hover:text-blue-300 font-medium") {
                                    attributes["hx-get"] = "/containers/${container.id}"
                                    attributes["hx-target"] = "#main-content"
                                    attributes["hx-push-url"] = "true"
                                    +"Inspect"
                                }

                                if (container.state == ContainerSummary.State.RUNNING) {
                                    button(classes = "text-orange-400 hover:text-orange-300 font-medium") {
                                        attributes["hx-post"] = "/containers/${container.id}/stop"
                                        attributes["hx-target"] = "#main-content"
                                        +"Stop"
                                    }
                                } else {
                                    button(classes = "text-green-400 hover:text-green-300 font-medium") {
                                        attributes["hx-post"] = "/containers/${container.id}/start"
                                        attributes["hx-target"] = "#main-content"
                                        +"Start"
                                    }
                                }

                                button(classes = "text-red-400 hover:text-red-300 font-medium") {
                                    attributes["hx-delete"] = "/containers/${container.id}"
                                    attributes["hx-target"] = "#main-content"
                                    attributes["hx-confirm"] = "Are you sure you want to remove this container?"
                                    +"Remove"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun FlowContent.renderContainerDetailsPage(id: String, info: ContainerInspectResponse?, pendingReplacement: Boolean = false) {
    div("space-y-6") {
        div("flex justify-between items-center") {
            h1("text-3xl font-bold text-blue-400") {
                +"Container: ${info?.name?.removePrefix("/") ?: id.take(12)}"
            }
            a(classes = "text-gray-400 hover:text-white cursor-pointer") {
                attributes["hx-get"] = "/containers"
                attributes["hx-target"] = "#main-content"
                attributes["hx-push-url"] = "true"
                +"← Back to List"
            }
        }

        div("flex flex-wrap gap-3") {
            badge(
                text = "Image: ${info?.config?.image ?: info?.image ?: "n/a"}",
                bgColor = "bg-blue-900/40 border-blue-700"
            )
            badge(
                text = "Status: ${info?.state?.status ?: "unknown"}",
                bgColor = if (info?.state?.running == true)
                    "bg-green-900/40 border-green-700"
                else
                    "bg-red-900/40 border-red-700"
            )
            badge("Platform: ${info?.platform ?: "n/a"}")
        }

        card("flex items-center gap-4 bg-blue-900/10 border-blue-800/50") {
            span("text-sm font-bold uppercase text-blue-300 mr-2") { +"Actions:" }
            if (info?.state?.running == true) {
                button(classes = "bg-orange-600 hover:bg-orange-500 px-4 py-2 rounded text-sm font-bold") {
                    attributes["hx-post"] = "/containers/$id/stop"
                    attributes["hx-target"] = "#main-content"
                    +"Stop"
                }
                form(classes = "flex items-center gap-4") {
                    attributes["hx-post"] = "/containers/$id/exec"
                    attributes["hx-target"] = "#main-content"
                    input(
                        type = InputType.text,
                        name = "command",
                        classes = "bg-gray-700 text-white p-2 rounded"
                    ) {
                        placeholder = "Command"
                        value = "/bin/sh"
                    }
                    button(
                        type = ButtonType.submit,
                        classes = "bg-purple-600 hover:bg-purple-500 px-4 py-2 rounded text-sm font-bold"
                    ) {
                        +"Exec"
                    }
                }
            } else {
                button(classes = "bg-green-600 hover:bg-green-500 px-4 py-2 rounded text-sm font-bold") {
                    attributes["hx-post"] = "/containers/$id/start"
                    attributes["hx-target"] = "#main-content"
                    +"Start"
                }
            }
            button(classes = "border border-red-500 text-red-500 hover:bg-red-500/10 px-4 py-2 rounded text-sm font-bold") {
                attributes["hx-delete"] = "/containers/$id"
                attributes["hx-target"] = "#main-content"
                attributes["hx-confirm"] = "Are you sure?"
                +"Delete"
            }
        }

        if (info?.config?.labels?.get(MANAGED_LABEL) == "true") {
            card("space-y-3") {
                if (pendingReplacement) {
                    p { +"Replacement started. Verify application readiness and retained data before confirming. The previous container is stopped and retained for rollback." }
                    p { +"Rollback restores the previous container configuration; it does not undo writes to shared volumes." }
                    button(classes = "bg-green-700 rounded p-2 mr-3") {
                        attributes["hx-post"] = "/containers/$id/confirm"
                        attributes["hx-target"] = "#main-content"
                        attributes["hx-confirm"] = "Readiness verified? Delete the previous container and retain its named volumes?"
                        +"Confirm replacement"
                    }
                    button(classes = "bg-orange-700 rounded p-2") {
                        attributes["hx-post"] = "/containers/$id/rollback"
                        attributes["hx-target"] = "#main-content"
                        +"Roll back"
                    }
                } else {
                    a(href = "/containers/$id/recreate", classes = "text-blue-400") { +"Recreate with changed configuration" }
                }
            }
        }

        if (info != null) {
            details("bg-gray-800/50 rounded-lg border border-gray-700") {
                summary("text-gray-400 text-xs uppercase font-bold p-4 cursor-pointer hover:bg-gray-700/50 transition-colors") {
                    +"Details"
                }
                div("p-4 border-t border-gray-700 grid grid-cols-1 md:grid-cols-2 gap-4") {
                    infoCard("General Information") {
                        infoRow("Full ID", id, isCode = true)
                        infoRow("Created", info.created ?: "-")
                        infoRow("Driver", info.driver ?: "-")
                        infoRow("Restart Count", info.restartCount?.toString() ?: "0")
                    }

                    infoCard("Configuration") {
                        val cmd = (listOfNotNull(info.path) + (info.args ?: emptyList())).joinToString(" ")
                        infoRow("Command", cmd.ifEmpty { "-" }, isCode = true)
                        infoRow("State", info.state?.status?.value ?: "-")
                    }

                    infoCard("Network") {
                        val ports = info.networkSettings?.ports
                        if (ports.isNullOrEmpty()) {
                            infoRow("Ports", "No ports exposed")
                        } else {
                            ports.forEach { (containerPort, hostBindings) ->
                                val hostPortStrings =
                                    hostBindings?.map { "${it.hostIp}:${it.hostPort}" }?.joinToString()
                                infoRow(containerPort, hostPortStrings ?: "Not mapped", isCode = true)
                            }
                        }
                    }

                    infoCard("Environment Variables") {
                        val env = info.config?.env
                        if (env.isNullOrEmpty()) {
                            infoRow("Variables", "No environment variables set")
                        } else {
                            env.forEach {
                                val (key, value) = it.split("=", limit = 2)
                                infoRow(key, value, isCode = true)
                            }
                        }
                    }

                    infoCard("Volumes") {
                        val mounts = info.mounts
                        if (mounts.isNullOrEmpty()) {
                            infoRow("Mounts", "No volumes mounted")
                        } else {
                            mounts.forEach {
                                infoRow(it.destination ?: "n/a", it.source ?: "anonymous", isCode = true)
                            }
                        }
                    }
                }
            }
        } else {
            div("grid grid-cols-1 md:grid-cols-2 gap-4") {
                infoCard("General Information") {
                    infoRow("Full ID", id)
                }
            }
            div("p-4 bg-red-900/20 border border-red-900 text-red-400 rounded") {
                +"Failed to get detailed inspect data"
            }
        }

        if (info?.state?.running == true) {
            renderLiveStream("/containers/$id/stats", "stats-view", append = false)
            renderLogsWindow(id)
        }
    }
}

fun FlowContent.renderLogsWindow(containerId: String) {
    renderLiveStream("/containers/$containerId/logs", "logs-view")
}

fun FlowContent.renderCreateForm(info: ContainerInspectResponse? = null, action: String = "/containers/create") {
    div("mb-6 bg-gray-800 rounded-lg p-4 border border-gray-700") {
        h2("text-xl font-bold mb-4 text-blue-400") { +(if (info == null) "Create New Container" else "Prepare Replacement") }
        form {
            attributes["hx-post"] = action
            attributes["hx-target"] = "#main-content"
            method = FormMethod.post
            this.action = action
            fun FlowContent.field(name: String, title: String, content: String, hint: String = "", multiline: Boolean = false) {
                div("mb-4") {
                    label(classes = "block text-gray-400 mb-2") { htmlFor = "config-$name"; +title }
                    if (multiline) {
                        textArea(classes = "bg-gray-700 text-white p-2 rounded w-full") {
                            id = "config-$name"; this.name = name; rows = "3"; +content
                        }
                    } else {
                        input(type = InputType.text, name = name, classes = "bg-gray-700 text-white p-2 rounded w-full") {
                            id = "config-$name"; value = content; required = name == "image"
                        }
                    }
                    if (hint.isNotEmpty()) p("text-sm text-gray-400") { +hint }
                }
            }
            if (info == null) field("name", "Name (optional)", "")
            field("image", "Image", info?.config?.image.orEmpty(), "Pull the image first from Images.")
            field("cmd", "Command arguments", info?.config?.cmd?.joinToString("\n") ?: "/bin/sh", "One argument per line. Empty uses the image default.", true)
            details("mb-4") {
                open = info != null
                summary("text-blue-400 cursor-pointer mb-3") { +"Environment, ports, volumes and network" }
                field("env", "Environment", info?.config?.env?.joinToString("\n").orEmpty(), "One KEY=value per line.", true)
                field("ports", "Published ports", info?.hostConfig?.portBindings.orEmpty().flatMap { (port, bindings) ->
                    bindings.orEmpty().map { "${it.hostIp}:${it.hostPort}:$port" }
                }.joinToString("\n"), "One 127.0.0.1:host-port:container-port/tcp per line; host port 0 chooses an available port.", true)
                field("volumes", "Named volumes", info?.mounts.orEmpty().filter { it.type?.value == "volume" }.joinToString("\n") {
                    "${it.name}:${it.destination}"
                }, "One volume-name:/container/path per line. Named volumes survive container deletion; missing volumes are created by Docker.", true)
                field("network", "Existing network (optional)", info?.networkSettings?.networks?.keys?.singleOrNull()?.takeUnless { it == "bridge" }.orEmpty())
            }
            label(classes = "block mb-4") {
                input(type = InputType.checkBox, name = "tty") { checked = info?.config?.tty ?: true }
                +" Interactive TTY"
            }
            if (info != null) p("text-orange-300 mb-4") {
                +"The previous container is stopped only after preparation succeeds. Verify the replacement before confirming. Shared-volume writes cannot be rolled back automatically."
            }
            button(type = ButtonType.submit, classes = "bg-blue-600 hover:bg-blue-500 px-4 py-2 rounded text-sm font-bold") {
                +(if (info == null) "Create and Run" else "Prepare replacement")
            }
        }
    }
}
