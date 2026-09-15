package routes.compose

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.compose.compose
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.html.*
import routes.respondSmart
import ui.*

internal fun Routing.composeControlRoutes(client: DockerClient) {
    post("/compose/actions/{operation}") {
        val operation = call.parameters["operation"]
        if (operation !in setOf("start", "stop", "restart")) return@post call.respond(HttpStatusCode.BadRequest)
        val project = call.request.queryParameters["project"]?.takeIf { it.isNotBlank() }
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val services = call.request.queryParameters.getAll("services")?.toSet()
        if (services?.any { it.isBlank() } == true) return@post call.respond(HttpStatusCode.BadRequest)
        val report = try {
            when (operation) {
                "start" -> client.compose.start(project, services)
                "stop" -> client.compose.stop(project, services, timeoutSeconds = 10)
                else -> client.compose.restart(project, services, timeoutSeconds = 10)
            }.getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            respondSmart("Compose operation interrupted") {
                breadcrumb("Compose", "/compose")
                h1 { +"Operation interrupted" }
                p { +"Some containers may already have changed. Refresh the project before trying again." }
                pageLink("Refresh project", projectPath(project), "btn")
            }
            return@post
        }
        respondSmart("Compose operation results") {
            breadcrumb("Compose", "/compose")
            h1 { +"${operation!!.replaceFirstChar(Char::uppercase)}: $project" }
            if (report.results.isEmpty()) {
                emptyState("No matching regular replicas", "The project or services may be absent, or contain only one-off or unknown replicas.")
            } else {
                p { +"${report.results.count { it.result.isSuccess }} of ${report.results.size} container requests succeeded." }
                div("table-wrap") { table {
                    thead { tr { listOf("Service", "Container", "Result").forEach { th { +it } } } }
                    tbody { report.results.forEach { item -> tr {
                        td { attributes["data-label"] = "Service"; +(item.container.service ?: "Unassigned") }
                        td { attributes["data-label"] = "Container"; +(item.container.name ?: item.container.id) }
                        td {
                            attributes["data-label"] = "Result"
                            if (item.result.isSuccess) +"Succeeded"
                            else { strong { +"Failed: " }; +(item.result.errorOrNull()?.message ?: "Docker rejected the request") }
                        }
                    } } }
                } }
            }
            p("hint") { +"Existing containers only. No dependency ordering or readiness checks. Volumes are preserved." }
            pageLink("Refresh project", projectPath(project), "btn btn-primary")
        }
    }
}
