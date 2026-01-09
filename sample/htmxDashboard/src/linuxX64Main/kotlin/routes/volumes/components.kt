package routes.volumes

import dev.limebeck.libs.docker.client.model.Volume
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import ui.card


fun FlowContent.renderVolumesPage(volumes: List<Volume>) {
    h1("text-3xl font-bold mb-6 text-yellow-400") { +"📦 Volumes" }

    card("mb-6 flex gap-4") {
        input(classes = "bg-gray-700 border-none rounded px-4 py-2 flex-grow") {
            id = "volume-name"
            name = "name"
            placeholder = "Volume Name"
        }
        button(classes = "bg-green-600 hover:bg-green-500 px-6 py-2 rounded font-bold") {
            attributes["hx-post"] = "/volumes"
            attributes["hx-include"] = "#volume-name"
            attributes["hx-target"] = "#main-content"
            +"Create Volume"
        }
        button(classes = "border border-red-500 text-red-500 hover:bg-red-500/10 px-6 py-2 rounded font-bold") {
            attributes["hx-post"] = "/volumes/prune"
            attributes["hx-target"] = "#main-content"
            +"Prune"
        }
    }

    div("bg-gray-800 rounded-lg overflow-hidden border border-gray-700") {
        table("w-full text-left") {
            thead("bg-gray-700 text-gray-400 uppercase text-xs") {
                tr {
                    listOf("Name", "Driver", "Mountpoint", "Action").forEach { th(classes = "px-6 py-3") { +it } }
                }
            }
            tbody("divide-y divide-gray-700") {
                volumes.forEach { vol ->
                    tr("hover:bg-gray-700/50") {
                        td("px-6 py-4 font-bold") { +vol.name }
                        td("px-6 py-4") { +vol.driver }
                        td("px-6 py-4 text-xs font-mono text-gray-400") { +vol.mountpoint }
                        td("px-6 py-4") {
                            button(classes = "text-red-400 hover:text-red-300") {
                                attributes["hx-delete"] = "/volumes/${vol.name}"
                                attributes["hx-target"] = "#main-content"
                                +"Delete"
                            }
                        }
                    }
                }
            }
        }
    }
}