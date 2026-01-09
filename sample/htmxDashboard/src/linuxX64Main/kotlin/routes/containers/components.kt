package routes.containers

import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import dev.limebeck.libs.docker.client.model.ContainerSummary
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import ui.badge
import ui.card
import ui.infoCard
import ui.infoRow


fun FlowContent.containerTable(containers: List<ContainerSummary>) {
    div("bg-gray-800 rounded-lg shadow-lg overflow-hidden border border-gray-700") {
        table("w-full text-left") {
            thead("bg-gray-700 text-gray-400 uppercase text-xs") {
                tr {
                    listOf("ID", "Name", "Image", "State", "Actions").forEach { th(classes = "px-6 py-3") { +it } }
                }
            }
            tbody("divide-y divide-gray-700") {
                containers.forEach { container ->
                    tr("hover:bg-gray-700/50 transition-colors") {
                        td("px-6 py-4 font-mono text-sm") { +(container.id?.take(12) ?: "-") }
                        td("px-6 py-4 font-mono text-sm") {
                            +(container.names?.joinToString(", ") { it.removePrefix("/") } ?: "-")
                        }
                        td("px-6 py-4") { +(container.image ?: "-") }
                        td("px-6 py-4") {
                            val dotColor =
                                if (container.state == ContainerSummary.State.RUNNING) "bg-green-400" else "bg-red-400"
                            div("flex items-center gap-2") {
                                div("w-2 h-2 rounded-full $dotColor") {}
                                +(container.state?.value ?: "unknown")
                            }
                        }
                        td("px-6 py-4 flex gap-3") {
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

fun FlowContent.renderContainerDetailsPage(id: String, info: ContainerInspectResponse?) {
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

        if (info != null) {
            div("grid grid-cols-1 md:grid-cols-2 gap-4") {
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

        renderLogsWindow(id)
    }
}

fun FlowContent.renderLogsWindow(containerId: String) {
    div("bg-black rounded-lg p-4 font-mono text-[10px] h-80 overflow-y-auto border border-gray-700 shadow-inner") {
        id = "logs-view"
        attributes.apply {
            put("hx-ext", "sse")
            put("sse-connect", "/containers/$containerId/logs")
            put("sse-swap", "message")
            put("hx-swap", "beforeend")
            put("hx-on:htmx:sse-message", "this.scrollTo(0, this.scrollHeight)")
        }
        div("text-gray-600 italic mb-2") { +"--- Initializing log stream ---" }
    }
}
