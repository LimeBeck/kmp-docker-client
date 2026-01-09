package routes.system

import dev.limebeck.libs.docker.client.model.SystemInfo
import dev.limebeck.libs.docker.client.model.SystemVersion
import kotlinx.html.*
import ui.infoCard
import ui.infoRow


fun FlowContent.renderSystemPage(info: SystemInfo?, version: SystemVersion?) {
    h1("text-3xl font-bold mb-6 text-green-400") { +"🖥️ System Info" }

    div("grid grid-cols-1 md:grid-cols-2 gap-6") {
        infoCard("Docker Engine") {
            infoRow("Version", version?.version ?: "n/a")
            infoRow("API Version", version?.apiVersion ?: "n/a")
            infoRow("Go Version", version?.goVersion ?: "n/a")
            infoRow("OS/Arch", "${version?.os}/${version?.arch}")
        }
        infoCard("Host Info") {
            infoRow("Hostname", info?.name ?: "n/a")
            infoRow("Operating System", info?.operatingSystem ?: "n/a")
            infoRow("Kernal Version", info?.kernelVersion ?: "n/a")
            infoRow("Total Memory", "${(info?.memTotal ?: 0) / 1024 / 1024 / 1024} GB")
        }
    }

    h2("text-xl font-bold mt-8 mb-4 text-orange-400") { +"🔔 Real-time Events" }
    div("bg-black rounded-lg p-4 font-mono text-[10px] h-64 overflow-y-auto border border-gray-700 shadow-inner") {
        id = "events-view"
        attributes.apply {
            put("hx-ext", "sse")
            put("sse-connect", "/system/events")
            put("sse-swap", "message")
            put("hx-swap", "afterbegin")
        }
        div("text-gray-600 italic") { +"--- Waiting for events ---" }
    }
}