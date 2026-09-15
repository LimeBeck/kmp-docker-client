package routes.images

import dev.limebeck.libs.docker.client.model.ImageInspect
import dev.limebeck.libs.docker.client.model.ImageSummary
import kotlinx.html.*
import ui.*

fun FlowContent.renderImagesPage(images: List<ImageSummary>) {
    pageHeading("Images", "${images.size} images available on this host") { pageLink("Pull image", "/images/pull", "btn btn-primary") }
    div("actions") { actionButton("Prune dangling images", "/images/prune", confirm = "Remove dangling images not referenced by a container? Tagged images are retained. Docker determines which images are eligible when this runs.", danger = true) }
    if (images.isEmpty()) { emptyState("No images yet", "Pull an image to create your first container.", "Pull image", "/images/pull"); return }
    filterToolbar()
    div("table-wrap") {
        attributes["role"] = "region"; attributes["aria-label"] = "Images"; attributes["tabindex"] = "0"
        table { thead { tr { listOf("Image", "Size", "Actions").forEach { th { scope = ThScope.col; +it } } } }
            tbody { images.forEach { image ->
                val name = image.repoTags?.joinToString(", ").orEmpty().ifEmpty { "Untagged image" }
                tr {
                    attributes["data-name"] = name.lowercase(); attributes["data-search-text"] = "$name ${image.id}".lowercase()
                    td { attributes["data-label"] = "Image"; pageLink(name, "/images/${image.id}", "resource-name"); code("resource-id") { +image.id.removePrefix("sha256:").take(12) } }
                    td("numeric") { attributes["data-label"] = "Size"; +bytes(image.propertySize.toULong()) }
                    td { attributes["data-label"] = "Actions"; actionMenu(accessibleLabel = "Actions for $name") {
                        pageLink("Inspect", "/images/${image.id}", "btn btn-small")
                        actionButton("Delete", "/images/${image.id}", "delete", "Delete $name (${image.id.removePrefix("sha256:").take(12)})? Containers using this image may prevent deletion.", danger = true)
                    } }
                }
            } }
        }
    }
    tableFoot()
}

fun FlowContent.renderImageDetailsPage(id: String, info: ImageInspect?) {
    breadcrumb("Images", "/images")
    pageHeading(info?.repoTags?.firstOrNull() ?: "Image details", id)
    if (info == null) { renderError("Could not inspect this image. Return to Images and retry."); return }
    div("grid") {
        infoCard("Metadata") {
            infoRow("Full ID", info.id ?: "Unavailable", true); infoRow("Author", info.author.orEmpty().ifEmpty { "Not specified" })
            infoRow("Architecture", "${info.architecture ?: "Unknown"} / ${info.os ?: "Unknown"}"); infoRow("Created", info.created ?: "Unavailable")
        }
        infoCard("Configuration") {
            infoRow("Size", bytes(info.propertySize?.toULong())); infoRow("Working directory", info.config?.workingDir.orEmpty().ifEmpty { "/" })
            infoRow("Entrypoint", info.config?.entrypoint?.joinToString(" ") ?: "Image default", true)
        }
        infoCard("Tags") { if (info.repoTags.isNullOrEmpty()) p("muted") { +"No tags" } else info.repoTags?.forEach { p { badge(it) } } }
    }
}
