import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.model.ContainerLogsParameters
import dev.limebeck.libs.docker.client.model.ContainerSummary
import dev.limebeck.libs.docker.client.model.LogLine
import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.html.*

fun main() {
    embeddedServer(CIO, port = 8080) {
        // Initialize Docker Client (using default system socket)
        val dockerClient = DockerClient()

        routing {
            // Main page: Dashboard
            get("/") {
                val containersResult = dockerClient.containers.getList()

                call.respondHtml {
                    head {
                        title("Docker KMP Dashboard")
                        // Connect HTMX and SSE extension
                        script(src = "https://unpkg.com/htmx.org@1.9.10") {}
                        script(src = "https://unpkg.com/htmx.org@1.9.10/dist/ext/sse.js") {}
                        // Tailwind CSS for styling (via CDN)
                        script(src = "https://cdn.tailwindcss.com") {}
                    }
                    body {
                        classes = setOf("bg-gray-900 text-gray-100 p-8 font-sans")

                        div("max-w-4xl mx-auto") {
                            h1("text-3xl font-bold mb-6 text-blue-400") { +"🐳 KMP Docker Dashboard" }

                            // Container details area
                            div("mb-8 p-4 bg-gray-800 rounded-lg shadow-lg min-h-[100px]") {
                                id = "container-details"
                                +"Select a container from the list below to see details."
                            }

                            // Containers list
                            div("bg-gray-800 rounded-lg shadow-lg overflow-hidden") {
                                table("w-full text-left") {
                                    thead("bg-gray-700 text-gray-400 uppercase") {
                                        tr {
                                            th(classes = "px-6 py-3") { +"ID" }
                                            th(classes = "px-6 py-3") { +"Image" }
                                            th(classes = "px-6 py-3") { +"State" }
                                            th(classes = "px-6 py-3") { +"Action" }
                                        }
                                    }
                                    tbody("divide-y divide-gray-700") {
                                        containersResult.getOrNull()?.forEach { container ->
                                            tr("hover:bg-gray-700 transition-colors") {
                                                td("px-6 py-4 font-mono text-sm") {
                                                    +(container.id?.take(12) ?: "unknown")
                                                }
                                                td("px-6 py-4") { +(container.image ?: "unknown") }
                                                td("px-6 py-4") {
                                                    val stateColor =
                                                        if (container.state == ContainerSummary.State.RUNNING) "text-green-400" else "text-red-400"
                                                    span(stateColor) { +(container.state?.value ?: "unknown") }
                                                }
                                                td("px-6 py-4") {
                                                    // HTMX button to load details
                                                    button(classes = "text-blue-400 hover:text-blue-300 underline") {
                                                        attributes["hx-get"] = "/containers/${container.id}"
                                                        attributes["hx-target"] = "#container-details"
                                                        attributes["hx-swap"] = "innerHTML"
                                                        +"Inspect"
                                                    }
                                                }
                                            }
                                        } ?: tr {
                                            td {
                                                attributes["colspan"] =
                                                    "4"; +"Failed to load containers or list is empty"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fragment: Container details + Logs block
            get("/containers/{id}") {
                val containerId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)

                val inspectResult = dockerClient.containers.getInfo(containerId)
                val containerInfo = inspectResult.getOrNull()

                call.respondHtml {
                    body {
                        div {
                            h2("text-xl font-semibold mb-2") { +"Container: ${containerInfo?.name?.removePrefix("/") ?: containerId}" }

                            // Main attributes
                            div("flex flex-wrap gap-4 mb-4") {
                                div("bg-gray-900 p-2 rounded text-sm font-mono border border-gray-700") {
                                    +"ID: ${containerId.take(12)}"
                                }
                                if (containerInfo != null) {
                                    div("bg-gray-900 p-2 rounded text-sm font-mono border border-gray-700") { +"Image: ${containerInfo.config?.image ?: containerInfo.image}" }
                                    div("bg-gray-900 p-2 rounded text-sm font-mono border border-gray-700") { +"Status: ${containerInfo.state?.status}" }
                                    div("bg-gray-900 p-2 rounded text-sm font-mono border border-gray-700") { +"Platform: ${containerInfo.platform}" }
                                }
                            }

                            // Detailed information as cards
                            if (containerInfo != null) {
                                div("grid grid-cols-1 md:grid-cols-2 gap-4 mb-6") {
                                    // Card 1: General information
                                    div("bg-gray-800 p-4 rounded border border-gray-700") {
                                        h3("text-gray-400 text-xs uppercase font-semibold mb-4 border-b border-gray-700 pb-2") { +"General Information" }

                                        div("space-y-3") {
                                            div {
                                                span("block text-xs text-gray-500") { +"Name" }
                                                span("font-mono text-sm text-gray-200") { +(containerInfo.name ?: "-") }
                                            }
                                            div {
                                                span("block text-xs text-gray-500") { +"Created" }
                                                span("text-sm text-gray-200") { +(containerInfo.created ?: "-") }
                                            }
                                            div {
                                                span("block text-xs text-gray-500") { +"Full ID" }
                                                span("font-mono text-xs text-gray-400 break-all") { +containerId }
                                            }
                                        }
                                    }

                                    // Card 2: State and Configuration
                                    div("bg-gray-800 p-4 rounded border border-gray-700") {
                                        h3("text-gray-400 text-xs uppercase font-semibold mb-4 border-b border-gray-700 pb-2") { +"Configuration & State" }

                                        div("space-y-3") {
                                            div {
                                                span("block text-xs text-gray-500") { +"Command" }
                                                // Combine command from Path and Args if they exist
                                                val cmd = (listOfNotNull(containerInfo.path) + (containerInfo.args
                                                    ?: emptyList())).joinToString(" ")
                                                code("bg-gray-900 px-2 py-1 rounded text-xs font-mono text-gray-300 break-all block mt-1") {
                                                    +cmd.ifEmpty { "-" }
                                                }
                                            }
                                            div("grid grid-cols-2 gap-2") {
                                                div {
                                                    span("block text-xs text-gray-500") { +"Driver" }
                                                    span("text-sm text-gray-200") { +(containerInfo.driver ?: "-") }
                                                }
                                                div {
                                                    span("block text-xs text-gray-500") { +"Restart Count" }
                                                    span("text-sm text-gray-200") {
                                                        +(containerInfo.restartCount?.toString() ?: "0")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Raw JSON fallback (hidden)
                                details("mb-4 bg-gray-900 rounded border border-gray-700") {
                                    summary("p-2 cursor-pointer text-gray-500 hover:text-gray-300 select-none text-xs font-mono text-center") { +"Show Raw Debug Data" }
                                    div("p-4 overflow-x-auto") {
                                        pre("text-xs text-green-400 font-mono whitespace-pre-wrap break-all") {
                                            +containerInfo.toString()
                                        }
                                    }
                                }
                            } else {
                                div("p-4 bg-red-900/20 border border-red-900 text-red-400 rounded mb-4") {
                                    +"Failed to get inspect data"
                                }
                            }

                            // Logs window with SSE connection
                            div("bg-black rounded-lg p-4 font-mono text-xs h-64 overflow-y-auto border border-gray-700") {
                                id = "logs-view"
                                attributes["hx-ext"] = "sse"
                                attributes["sse-connect"] = "/containers/$containerId/logs" // Connect to stream
                                attributes["sse-swap"] =
                                    "message" // Each message is appended to the end (default behavior)
                                attributes["hx-swap"] = "beforeend" // Append to the end instead of replacing
                                attributes["hx-on:htmx:sse-message"] =
                                    "this.scrollTo(0, this.scrollHeight)" // Auto-scroll

                                div("text-gray-500 italic mb-2") { +"Waiting for logs..." }
                                // Log lines will be appended here
                            }
                        }
                    }
                }
            }

            // SSE Endpoint: Logs streaming
            get("/containers/{id}/logs") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)

                // Set headers for SSE
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    try {
                        // Write start event
                        writeStringUtf8("data: <div class='text-blue-500'>--- Connected to logs stream ---</div>\n\n")
                        flush()

                        // Get logs flow from Docker
                        // follow = true keeps the connection open
                        val logsFlow = dockerClient.containers.getLogs(
                            id = id,
                            parameters = ContainerLogsParameters(
                                follow = true,
                                stdout = true,
                                stderr = true,
                                tail = "10"
                            )
                        ).getOrThrow()

                        // Read Flow and write to SSE channel
                        logsFlow.collect { logLine ->
                            // Format line as HTML for insertion
                            val safeLine = logLine.line.replace("<", "&lt;").replace(">", "&gt;")
                            val colorClass =
                                if (logLine.type == LogLine.Type.STDERR) "text-red-400" else "text-gray-300"

                            // SSE Format: "data: {payload}\n\n"
                            val htmlPayload = "<div class='$colorClass'>$safeLine</div>"
                            writeStringUtf8("data: $htmlPayload\n\n")
                            flush()
                        }
                    } catch (e: Exception) {
                        // Connection closed handling
                        writeStringUtf8("data: <div class='text-yellow-500'>--- Connection closed ---</div>\n\n")
                        flush()
                    }
                }
            }
        }
    }.start(wait = true)
}