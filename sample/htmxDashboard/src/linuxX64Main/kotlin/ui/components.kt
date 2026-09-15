package ui

import kotlinx.html.*

fun HTML.renderLayout(pageTitle: String, content: FlowContent.() -> Unit) {
    attributes["lang"] = "en"
    head {
        title("$pageTitle · Docker dashboard")
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        style { unsafe { +dashboardStyles } }
        script { unsafe { +"try { document.documentElement.dataset.theme = localStorage.getItem('dashboard-theme') || 'dark'; } catch (_) {}" } }
        script(src = "https://unpkg.com/htmx.org@1.9.10") {}
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css")
        script(src = "https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.js") {}
        script(src = "https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.js") {}
    }
    body {
        attributes["hx-history"] = "false"
        a(href = "#main-content", classes = "btn skip-link") { +"Skip to content" }
        div("alerts") { id = "alerts"; attributes["aria-label"] = "Notifications" }
        div("request-status") { id = "request-status"; attributes["role"] = "status" }
        div("app-shell") {
            aside("sidebar") {
                a(href = "/containers", classes = "brand") { icon("containers"); +"Docker dashboard" }
                nav("side-nav") {
                    attributes["aria-label"] = "Main navigation"
                    listOf("Containers", "Images", "Volumes", "Networks", "System").forEach {
                        navLink(it, "/${it.lowercase()}", "#main-content")
                    }
                }
                div("sidebar-footer") { small { +"SINGLE HOST" }; span { +"Local Docker" }; small { +"Development dashboard" } }
            }
            div("main-shell") {
                header("topbar") {
                    span { +"Workspace / "; strong { +"Local Docker" } }
                    button(type = ButtonType.button, classes = "btn btn-small theme-toggle") {
                        id = "theme-toggle"
                        attributes["aria-label"] = "Switch to light theme"
                        attributes["title"] = "Switch to light theme"
                        span { attributes["data-theme-sun"] = ""; icon("sun") }
                        span { attributes["data-theme-moon"] = ""; hidden = true; icon("moon") }
                    }
                }
                main("main-content") { id = "main-content"; attributes["tabindex"] = "-1"; content() }
            }
        }
        unsafe { +"""<dialog id="confirm-dialog" aria-labelledby="confirm-title"><form method="dialog"><h2 id="confirm-title">Confirm action</h2><p id="confirm-message"></p><label class="field" id="confirm-label" hidden>Type the resource name to confirm<input id="confirm-input" autocomplete="off"></label><div class="actions"><button class="btn" value="cancel">Cancel</button><button class="btn btn-danger" id="confirm-submit" value="confirm">Confirm</button></div></form></dialog>""" }
        script { unsafe { +dashboardScript } }
    }
}

fun FlowContent.icon(name: String) {
    val path = when (name) {
        "sun" -> "<circle cx='12' cy='12' r='4'/><path d='M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5'/>"
        "moon" -> "<path d='M20.5 13A8.5 8.5 0 0 1 11 3.5 8.5 8.5 0 1 0 20.5 13Z'/>"
        "containers" -> "<path d='m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5'/>"
        "images" -> "<rect x='3' y='3' width='18' height='18' rx='3'/><path d='m3 16 6-6 12 10'/><circle cx='16' cy='8' r='1'/>"
        "volumes" -> "<ellipse cx='12' cy='5' rx='8' ry='3'/><path d='M4 5v14c0 4 16 4 16 0V5M4 12c0 4 16 4 16 0'/>"
        "networks" -> "<rect x='8' y='2' width='8' height='6' rx='1'/><path d='M12 8v5M5 17v-4h14v4'/><rect x='2' y='17' width='6' height='5' rx='1'/><rect x='16' y='17' width='6' height='5' rx='1'/>"
        else -> "<path d='M2 12h5l3-8 4 16 3-8h5'/>"
    }
    span { unsafe { +"<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'>$path</svg>" } }
}

fun FlowContent.navLink(text: String, link: String, target: String, vararg htmxAttrs: Pair<String, String>) {
    a(href = link) {
        attributes["hx-push-url"] = "true"; attributes["hx-get"] = link; attributes["hx-target"] = target
        attributes["data-section"] = link
        htmxAttrs.forEach { (k, v) -> attributes[k] = v }
        icon(text.lowercase()); +text
    }
}
fun FlowContent.pageHeading(title: String, subtitle: String = "", actions: FlowContent.() -> Unit = {}) {
    div("page-heading") {
        div { h1 { attributes["tabindex"] = "-1"; +title }; if (subtitle.isNotEmpty()) p { +subtitle } }
        div("actions") { actions() }
    }
}
fun FlowContent.pageLink(text: String, path: String, classes: String = "") {
    a(href = path, classes = classes) { attributes["hx-get"] = path; attributes["hx-target"] = "#main-content"; attributes["hx-push-url"] = "true"; +text }
}
fun FlowContent.breadcrumb(text: String, path: String) { nav("breadcrumb") { attributes["aria-label"] = "Breadcrumb"; pageLink("← $text", path) } }
fun FlowContent.card(classes: String = "", block: FlowContent.() -> Unit) { div("panel $classes") { block() } }
fun FlowContent.badge(text: String, bgColor: String = "") { span("badge $bgColor") { +text } }
fun FlowContent.stateBadge(state: String, text: String = state) { span("status status-$state") { +text } }
fun FlowContent.infoCard(title: String, block: FlowContent.() -> Unit) { div("panel info-card") { h3 { +title }; div("info-rows") { block() } } }
fun FlowContent.infoRow(label: String, value: String, isCode: Boolean = false) {
    dl("info-row") { dt { +label }; dd { if (isCode) code { +value } else +value } }
}
fun FlowContent.renderError(message: String) {
    div("notice notice-error") {
        attributes["role"] = "alert"
        div { strong { +"Something went wrong" }; p { +message } }
        button(classes = "btn btn-small", type = ButtonType.button) { attributes["data-dismiss"] = ""; attributes["aria-label"] = "Dismiss error"; +"×" }
    }
}
fun FlowContent.emptyState(title: String, message: String, action: String? = null, path: String? = null) {
    div("empty-state") { h2 { +title }; p { +message }; if (action != null && path != null) pageLink(action, path, "btn btn-primary") }
}
fun FlowContent.actionButton(text: String, path: String, method: String = "post", confirm: String? = null, confirmName: String? = null, danger: Boolean = false) {
    button(type = ButtonType.button, classes = "btn btn-small${if (danger) " btn-danger" else ""}") {
        attributes["hx-$method"] = path; attributes["hx-target"] = "#main-content"; attributes["hx-sync"] = "this:drop"
        confirm?.let { attributes["hx-confirm"] = it }; confirmName?.let { attributes["data-confirm-name"] = it }
        +text
    }
}
fun FlowContent.filterToolbar(states: List<String> = emptyList()) {
    div("toolbar") {
        label("field") { +"Find a resource"; input(type = InputType.search) { attributes["data-search"] = ""; placeholder = "Search name, image or ID…" } }
        if (states.isNotEmpty()) label("field") { +"State"; select { attributes["data-filter"] = ""; option { value = ""; +"All states" }; states.forEach { option { value = it; +it.replaceFirstChar(Char::uppercase) } } } }
        label("field") { +"Sort by"; select { attributes["data-sort"] = ""; option { value = "name"; +"Name" }; if (states.isNotEmpty()) option { value = "state"; +"State" } } }
    }
}
fun FlowContent.tableFoot() {
    div("empty-state") { attributes["data-no-results"] = ""; hidden = true; h2 { +"No matches" }; p { +"Try another name or reset the filters." }; button(classes = "btn", type = ButtonType.button) { attributes["data-reset-filters"] = ""; +"Reset filters" } }
    div("table-footer") { span { attributes["data-result-count"] = ""; attributes["role"] = "status" }; span { +"Snapshot · refresh to update" } }
}
fun bytes(value: ULong?): String {
    if (value == null) return "Unavailable"
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var number = value.toDouble(); var unit = 0
    while (number >= 1024 && unit < units.lastIndex) { number /= 1024; unit++ }
    val rounded = (number * 10).toLong()
    return "${rounded / 10}${if (unit == 0) "" else ".${rounded % 10}"} ${units[unit]}"
}
fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
