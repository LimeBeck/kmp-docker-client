package routes

import io.ktor.server.html.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.html.FlowContent
import kotlinx.html.body
import kotlinx.html.title
import kotlinx.html.head
import kotlinx.html.a
import ui.renderError
import ui.pageHeading
import kotlinx.coroutines.CancellationException
import io.ktor.http.HttpStatusCode
import ui.renderLayout

suspend fun RoutingContext.respondSmart(title: String, status: HttpStatusCode = HttpStatusCode.OK, block: FlowContent.() -> Unit) {
    call.response.headers.append("Cache-Control", "no-store")
    val isHtmx = call.request.headers["HX-Request"] == "true"
    if (isHtmx) {
        call.respondHtml(status) { head { title("$title · Docker dashboard") }; body { block() } }
    } else {
        call.respondHtml(status) { renderLayout(title) { block() } }
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

/** Keeps the current form/page in place when an operation fails. */
suspend fun RoutingContext.operationError(message: String) {
    call.response.headers.append("HX-Retarget", "#alerts")
    call.response.headers.append("HX-Reswap", "beforeend")
    call.respondHtml(HttpStatusCode.UnprocessableEntity) { body { renderError(message) } }
}

suspend fun RoutingContext.pageAction(title: String, block: suspend () -> Unit) {
    try { block() } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        respondSmart(title) {
            pageHeading(title)
            renderError(e.message ?: "Docker request failed. Check the daemon and try again.")
            a(href = "") { +"Retry" }
        }
    }
}
