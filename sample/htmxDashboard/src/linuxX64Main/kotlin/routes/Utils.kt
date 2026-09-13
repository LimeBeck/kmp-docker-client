package routes

import io.ktor.server.html.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.html.FlowContent
import kotlinx.html.body
import ui.renderLayout

suspend fun RoutingContext.respondSmart(title: String, block: FlowContent.() -> Unit) {
    val isHtmx = call.request.headers["HX-Request"] == "true"
    if (isHtmx) {
        call.respondHtml { body { block() } }
    } else {
        call.respondHtml { renderLayout(title) { block() } }
    }
}

/** Mutating requests must not replay DELETE/POST against the destination page. */
suspend fun RoutingContext.redirectSmart(path: String) {
    call.response.headers.append("HX-Redirect", path)
    if (call.request.headers["HX-Request"] == "true") {
        call.respond(io.ktor.http.HttpStatusCode.OK)
    } else {
        call.response.headers.append(io.ktor.http.HttpHeaders.Location, path)
        call.respond(io.ktor.http.HttpStatusCode.SeeOther)
    }
}
