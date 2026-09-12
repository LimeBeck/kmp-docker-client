package routes.terminal

import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import ui.renderTerminalPanel

fun FlowContent.renderTerminal(containerId: String, info: ContainerInspectResponse?) {
    div("space-y-6") {
        div("flex justify-between items-center") {
            h1("text-3xl font-bold text-blue-400") {
                +"Terminal: ${info?.name?.removePrefix("/") ?: containerId.take(12)}"
            }
            a(classes = "text-gray-400 hover:text-white cursor-pointer") {
                attributes["hx-get"] = "/containers/$containerId"
                attributes["hx-target"] = "#main-content"
                attributes["hx-push-url"] = "true"
                +"← Back to Container"
            }
        }
        renderTerminalPanel("/containers/$containerId/terminal/ws")
    }
}
