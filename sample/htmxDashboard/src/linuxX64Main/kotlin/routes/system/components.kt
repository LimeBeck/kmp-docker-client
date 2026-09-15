package routes.system

import dev.limebeck.libs.docker.client.model.SystemInfo
import dev.limebeck.libs.docker.client.model.SystemVersion
import kotlinx.html.*
import ui.*

fun FlowContent.renderSystemPage(info: SystemInfo?, version: SystemVersion?) {
    pageHeading("System", "Docker engine and host information")
    div("stack") {
        div("grid") {
            infoCard("Docker Engine") {
                infoRow("Version", version?.version ?: "Unavailable"); infoRow("API version", version?.apiVersion ?: "Unavailable")
                infoRow("Go version", version?.goVersion ?: "Unavailable"); infoRow("OS / architecture", "${version?.os ?: "Unknown"} / ${version?.arch ?: "Unknown"}")
            }
            infoCard("Host") {
                infoRow("Hostname", info?.name ?: "Unavailable"); infoRow("Operating system", info?.operatingSystem ?: "Unavailable")
                infoRow("Kernel version", info?.kernelVersion ?: "Unavailable"); infoRow("Total memory", bytes(info?.memTotal?.toULong()))
            }
        }
        renderLiveStream("/system/events", "events-view", title = "Events")
    }
}
