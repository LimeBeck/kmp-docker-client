package routes.images

import dev.limebeck.libs.docker.client.model.ImageInspect
import dev.limebeck.libs.docker.client.model.ImageSummary
import kotlinx.html.FlowContent
import kotlinx.html.a
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
import ui.badge
import ui.card
import ui.infoCard
import ui.infoRow


fun FlowContent.renderImagesPage(images: List<ImageSummary>) {
    h1("text-3xl font-bold mb-6 text-purple-400") { +"🖼️ Images" }

    card("mb-6 flex gap-4") {
        input(classes = "bg-gray-700 border-none rounded px-4 py-2 flex-grow") {
            id = "image-pull-name"
            name = "image-pull-name"
            placeholder = "e.g. nginx:latest"
        }
        button(classes = "bg-blue-600 hover:bg-blue-500 px-6 py-2 rounded font-bold") {
            attributes["hx-post"] = "/images/pull"
            attributes["hx-include"] = "#image-pull-name"
            attributes["hx-target"] = "#main-content"
            +"Pull Image"
        }
        button(classes = "border border-red-500 text-red-500 hover:bg-red-500/10 px-6 py-2 rounded font-bold") {
            attributes["hx-post"] = "/images/prune"
            attributes["hx-target"] = "#main-content"
            attributes["hx-confirm"] = "Are you sure you want to delete all unused images?"
            +"Prune"
        }
    }

    div("bg-gray-800 rounded-lg overflow-hidden border border-gray-700") {
        table("w-full text-left") {
            thead("bg-gray-700 text-gray-400 uppercase text-xs") {
                tr {
                    listOf("ID", "Tags", "Size", "Action").forEach { th(classes = "px-6 py-3") { +it } }
                }
            }
            tbody("divide-y divide-gray-700") {
                images.forEach { image ->
                    tr("hover:bg-gray-700/50") {
                        td("px-6 py-4 font-mono text-sm") { +(image.id.removePrefix("sha256:").take(12)) }
                        td("px-6 py-4") { +(image.repoTags.joinToString(", ")) }
                        td("px-6 py-4") { +("${(image.propertySize) / 1024 / 1024} MB") }
                        td("px-6 py-4 flex gap-3") {
                            button(classes = "text-blue-400 hover:text-blue-300 font-medium") {
                                attributes["hx-get"] = "/images/${image.id}"
                                attributes["hx-target"] = "#main-content"
                                attributes["hx-push-url"] = "true"
                                +"Inspect"
                            }
                            button(classes = "text-red-400 hover:text-red-300") {
                                attributes["hx-delete"] = "/images/${image.id}"
                                attributes["hx-target"] = "#main-content"
                                attributes["hx-confirm"] = "Delete this image?"
                                +"Delete"
                            }
                        }
                    }
                }
            }
        }
    }
}

fun FlowContent.renderImageDetailsPage(id: String, info: ImageInspect?) {
    div("space-y-6") {
        div("flex justify-between items-center") {
            h1("text-3xl font-bold text-purple-400") {
                +"Image: ${info?.repoTags?.firstOrNull() ?: id.take(12)}"
            }
            a(classes = "text-gray-400 hover:text-white cursor-pointer") {
                attributes["hx-get"] = "/images"
                attributes["hx-target"] = "#main-content"
                attributes["hx-push-url"] = "true"
                +"← Back to List"
            }
        }

        if (info != null) {
            div("grid grid-cols-1 md:grid-cols-2 gap-4") {
                infoCard("Image Metadata") {
                    infoRow("Full ID", info.id ?: "-", isCode = true)
                    infoRow("Author", info.author ?: "-")
                    infoRow("Architecture", "${info.architecture} / ${info.os}")
                    infoRow("Docker Version", info.dockerVersion ?: "-")
                    infoRow("Created", info.created ?: "-")
                }
                infoCard("Configuration") {
                    infoRow("Size", "${(info.propertySize ?: 0) / 1024 / 1024} MB")
                    infoRow("Virtual Size", "${(info.virtualSize ?: 0) / 1024 / 1024} MB")
                    infoRow("Working Dir", info.config?.workingDir ?: "-")
                    infoRow("Entrypoint", info.config?.entrypoint?.joinToString(" ") ?: "-")
                }
            }

            if (!info.repoTags.isNullOrEmpty()) {
                infoCard("Tags") {
                    div("flex flex-wrap gap-2") {
                        info.repoTags!!.forEach { badge(it, "bg-purple-900/40 border-purple-700") }
                    }
                }
            }
        } else {
            div("p-4 bg-red-900/20 border border-red-900 text-red-400 rounded") {
                +"Failed to get detailed image data"
            }
        }
    }
}