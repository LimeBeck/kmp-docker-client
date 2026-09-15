package routes.networks

import dev.limebeck.libs.docker.client.model.Network
import kotlinx.html.*
import ui.*

fun FlowContent.renderNetworksPage(networks: List<Network>) {
    pageHeading("Networks", "${networks.size} networks on this host")
    if (networks.isEmpty()) { emptyState("No networks", "There are no networks to display."); return }
    filterToolbar()
    div("table-wrap") {
        attributes["role"] = "region"; attributes["aria-label"] = "Networks"; attributes["tabindex"] = "0"
        table { thead { tr { listOf("Name", "Driver", "Scope").forEach { th { scope = ThScope.col; +it } } } }
            tbody { networks.forEach { network -> tr {
                attributes["data-name"] = network.name.orEmpty().lowercase(); attributes["data-search-text"] = "${network.name} ${network.id} ${network.driver}".lowercase()
                td { attributes["data-label"] = "Name"; strong("resource-name") { +(network.name ?: "Unnamed network") }; code("resource-id") { +(network.id ?: "Unavailable") } }
                td { attributes["data-label"] = "Driver"; +(network.driver ?: "Unavailable") }; td { attributes["data-label"] = "Scope"; +(network.scope ?: "Unavailable") }
            } } }
        }
    }
    tableFoot()
}
