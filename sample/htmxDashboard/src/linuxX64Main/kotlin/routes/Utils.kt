package routes

import io.ktor.server.html.*
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
