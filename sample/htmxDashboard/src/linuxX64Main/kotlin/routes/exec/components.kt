package routes.exec

import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import ui.renderTerminalPanel
import ui.breadcrumb
import ui.pageHeading


fun FlowContent.renderExecTerminal(containerId: String, execId: String, info: ContainerInspectResponse?) {
    breadcrumb("Container", "/containers/$containerId?tab=terminal")
    pageHeading("Terminal · ${info?.name?.removePrefix("/") ?: containerId.take(12)}", "Interactive session · leaving this page disconnects the terminal")
    renderTerminalPanel("/exec/$execId/ws")
}
