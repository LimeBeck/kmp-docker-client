package routes.volumes

import dev.limebeck.libs.docker.client.model.Volume
import kotlinx.html.*
import ui.*

fun FlowContent.renderVolumesPage(volumes: List<Volume>) {
    pageHeading("Volumes", "Persistent data on your Docker host")
    details("panel section-disclosure") {
        summary { +"Create a volume" }
        form(classes = "toolbar") {
            attributes["hx-post"] = "/volumes"; attributes["hx-target"] = "#main-content"; attributes["hx-sync"] = "this:drop"
            label("field") { +"Volume name (optional)"; input(type = InputType.text, name = "name") { placeholder = "e.g. postgres-data" } }
            button(type = ButtonType.submit, classes = "btn btn-primary") { +"Create volume" }
        }
    }
    div("toolbar") { actionButton("Prune unused anonymous volumes", "/volumes/prune", confirm = "Permanently remove unused anonymous volumes and their data? Named volumes are retained. Docker determines eligibility when this runs.", danger = true) }
    if (volumes.isEmpty()) { emptyState("No volumes yet", "Create a named volume to keep data independently of containers."); return }
    filterToolbar()
    div("table-wrap") {
        attributes["role"] = "region"; attributes["aria-label"] = "Volumes"; attributes["tabindex"] = "0"
        table { thead { tr { listOf("Name", "Driver", "Mountpoint", "Actions").forEach { th { scope = ThScope.col; +it } } } }
            tbody { volumes.forEach { volume -> tr {
                attributes["data-name"] = volume.name.lowercase(); attributes["data-search-text"] = "${volume.name} ${volume.driver} ${volume.mountpoint}".lowercase()
                td { attributes["data-label"] = "Name"; strong("resource-name") { +volume.name } }; td { attributes["data-label"] = "Driver"; +volume.driver }; td { attributes["data-label"] = "Mountpoint"; code { +volume.mountpoint } }
                td { attributes["data-label"] = "Actions"; details("action-menu") { summary("btn btn-small") { attributes["aria-label"] = "Actions for ${volume.name}"; +"Actions" }; div("menu-content") {
                    actionButton("Delete volume", "/volumes/${volume.name}", "delete", "Permanently delete ${volume.name} and its data? This cannot be undone. A volume in use cannot be deleted.", volume.name, true)
                } } }
            } } }
        }
    }
    tableFoot()
}
