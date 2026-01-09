package ui

import kotlinx.html.*


fun HTML.renderLayout(pageTitle: String, content: FlowContent.() -> Unit) {
    head {
        title(pageTitle)
        script(src = "https://unpkg.com/htmx.org@1.9.10") {}
        script(src = "https://unpkg.com/htmx.org@1.9.10/dist/ext/sse.js") {}
        script(src = "https://cdn.tailwindcss.com") {}
    }
    body("bg-gray-900 text-gray-100 p-8 font-sans max-w-6xl mx-auto") {
        div {
            id = "alerts"
            classes = setOf("fixed top-4 right-4 z-50 flex flex-col gap-2")
        }
        nav("flex gap-6 mb-8 border-b border-gray-700 pb-4") {
            navLink("Containers", "/", "hx-get" to "/containers", "hx-target" to "#main-content")
            navLink("Images", "/images", "hx-get" to "/images", "hx-target" to "#main-content")
            navLink("Volumes", "/volumes", "hx-get" to "/volumes", "hx-target" to "#main-content")
            navLink("System", "/system", "hx-get" to "/system", "hx-target" to "#main-content")
        }
        div {
            id = "main-content"
            content()
        }
    }
}

fun FlowContent.navLink(text: String, href: String, vararg htmxAttrs: Pair<String, String>) {
    a(classes = "text-gray-400 hover:text-white transition-colors cursor-pointer font-medium") {
        attributes["hx-push-url"] = "true"
        htmxAttrs.forEach { (k, v) -> attributes[k] = v }
        +text
    }
}

fun FlowContent.card(classes: String = "", block: FlowContent.() -> Unit) {
    div("bg-gray-800 p-4 rounded-lg shadow-lg border border-gray-700 $classes") {
        block()
    }
}

fun FlowContent.badge(text: String, bgColor: String = "bg-gray-700") {
    span("$bgColor px-2 py-1 rounded text-xs font-mono border border-white/10") { +text }
}

fun FlowContent.infoCard(title: String, block: FlowContent.() -> Unit) {
    div("bg-gray-800/50 p-4 rounded-lg border border-gray-700") {
        h3("text-gray-400 text-xs uppercase font-bold mb-3 border-b border-gray-700 pb-2") { +title }
        div("space-y-3") { block() }
    }
}

fun FlowContent.infoRow(label: String, value: String, isCode: Boolean = false) {
    div {
        span("block text-[10px] text-gray-500 uppercase") { +label }
        if (isCode) {
            code("block mt-1 p-1.5 bg-black/50 rounded text-xs font-mono text-gray-300 break-all") { +value }
        } else {
            span("text-sm text-gray-200") { +value }
        }
    }
}
