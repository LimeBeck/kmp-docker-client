package routes.terminal

import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import ui.renderTerminalPanel
import ui.breadcrumb
import ui.pageHeading

fun FlowContent.renderTerminal(containerId: String, info: ContainerInspectResponse?) {
    breadcrumb("Container", "/containers/$containerId?tab=terminal")
    pageHeading("Terminal · ${info?.name?.removePrefix("/") ?: containerId.take(12)}", "Interactive session · leaving this page disconnects the terminal")
    renderTerminalPanel("/containers/$containerId/terminal/ws")
}
