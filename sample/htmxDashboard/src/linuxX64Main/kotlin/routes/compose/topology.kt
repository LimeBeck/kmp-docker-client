package routes.compose

import dev.limebeck.libs.docker.client.model.*
import io.ktor.http.encodeURLPathPart
import kotlinx.html.*
import kotlinx.serialization.json.Json
import ui.*

private data class Binding(val container: String, val port: String, val address: String, val hostPort: String, val active: Boolean) {
    val tone get() = when { address == "::1" || address.startsWith("127.") -> "loopback"; address in listOf("0.0.0.0", "::") -> "public"; else -> "specific" }
}
private fun bindings(id: String, info: ContainerInspectResponse): List<Binding> {
    val live = info.networkSettings?.ports.orEmpty()
    val configured = info.hostConfig?.portBindings.orEmpty()
    return (live.keys + configured.keys).flatMap { port ->
        val observed = live[port].orEmpty()
        (if (observed.isNotEmpty() && info.state?.running == true) observed else configured[port].orEmpty()).map { binding ->
            Binding(id, port, binding.hostIp?.ifBlank { "Default address" } ?: "Default address", binding.hostPort?.ifBlank { "Automatic" } ?: "Automatic", observed.isNotEmpty() && info.state?.running == true)
        }
    }
}
private fun ContainerInspectResponse.displayName() = name?.removePrefix("/") ?: id.orEmpty().take(12)
private fun ContainerInspectResponse.ports() = (config?.exposedPorts?.keys.orEmpty() + hostConfig?.portBindings?.keys.orEmpty() + networkSettings?.ports?.keys.orEmpty()).sorted()
private fun selection(base: String, id: String, detail: String = "overview") = "$base&tab=topology&container=${id.queryValue()}&detail=$detail"

internal fun FlowContent.composeTopology(base: String, inspections: Map<String, ContainerInspectResponse>, networks: List<Network>, selectedId: String?, detail: String) {
    val chosen = selectedId?.let { inspections[it] }
    val bindings = inspections.flatMap { (id, info) -> bindings(id, info) }
    val attachedNames = inspections.values.flatMap { it.networkSettings?.networks?.keys.orEmpty() }.distinct().sorted()
    div("topology-layout${if (chosen == null) " topology-no-selection" else ""}") {
        div("topology-main") {
            div("topology-canvas") {
                id = "compose-topology"
                unsafe { +"<svg class='topology-edges' aria-hidden='true'></svg>" }
                section("topology-host") {
                    div("topology-host-title") { icon("networks"); div { h2 { +"Docker host" }; p("hint") { +"Host bindings → container ports" } } }
                    div("topology-bindings") {
                        if (bindings.isEmpty()) p("muted") { +"No port bindings configured" }
                        bindings.forEach { binding ->
                            a(href = selection(base, binding.container), classes = "topology-binding binding-${binding.tone}") {
                                attributes["hx-get"] = href; attributes["hx-target"] = "#main-content"; attributes["hx-push-url"] = "true"
                                attributes["data-binding-container"] = binding.container
                                attributes["data-binding-active"] = binding.active.toString()
                                attributes["title"] = "${inspections.getValue(binding.container).displayName()} · ${if (binding.active) "Published" else "Configured only"}"
                                span { +"${if (binding.address == "Default address") "Default" else binding.address}:${binding.hostPort}" }; strong { +"→ ${binding.port}" }
                                if (!binding.active) small { attributes["title"] = "Configured binding; not currently published"; +"Configured only" }
                            }
                        }
                    }
                }
                div("topology-legend") { strong { +"Port binding" }; span("binding-loopback") { +"● Loopback" }; span("binding-public") { +"● All interfaces" }; span("binding-specific") { +"● Specific/default address" }; span { +"Dashed: configured binding" } }
                attachedNames.forEach { networkName ->
                    val network = networks.find { it.name == networkName }
                    section("topology-network") {
                        div("topology-network-heading") { icon("networks"); div { h3 { +networkName }; p("hint") { +listOfNotNull(network?.driver, network?.IPAM?.config?.mapNotNull { it.subnet }?.joinToString(", ")?.ifBlank { null }).joinToString(" · ").ifBlank { "Network metadata unavailable" } } } }
                        div("topology-nodes") {
                            inspections.filter { networkName in it.value.networkSettings?.networks.orEmpty() }.forEach { (id, info) ->
                                topologyNode(base, id, info, networkName, id == selectedId)
                            }
                        }
                        p("topology-network-note") { +"Shared network"; br {}; small { +"Network membership, not observed traffic" } }
                    }
                }
                val detached = inspections.filter { it.value.networkSettings?.networks.isNullOrEmpty() }
                if (detached.isNotEmpty()) section("topology-network") {
                    h3 { +"No network endpoints reported" }
                    div("topology-nodes") { detached.forEach { (id, info) -> topologyNode(base, id, info, null, id == selectedId) } }
                }
            }
            div("topology-tables") {
                section("panel") { h2 { +"Services" }; table("topology-table") {
                    thead { tr { listOf("Name", "State", "Image").forEach { th { +it } } } }
                    tbody { inspections.entries.groupBy { it.value.config?.labels?.get("com.docker.compose.service") ?: "Unassigned" }.toList().sortedBy { it.first }.forEach { (name, replicas) -> tr {
                        td { pageLink(name, selection(base, replicas.first().key)) }
                        td { val states = replicas.map { it.value.state?.status?.value ?: "unknown" }.distinct(); +(if (states.size == 1) states.single() else "mixed") }
                        td { +replicas.mapNotNull { it.value.config?.image }.distinct().joinToString(", ") }
                    } } }
                } }
                section("panel") { h2 { +"Networks" }; table("topology-table") {
                    thead { tr { listOf("Name", "Driver", "Subnet", "Containers").forEach { th { +it } } } }
                    tbody { attachedNames.forEach { name ->
                        val network = networks.find { it.name == name }
                        tr { td { +name }; td { +(network?.driver ?: "Unknown") }; td { +network?.IPAM?.config.orEmpty().mapNotNull { it.subnet }.joinToString(", ").ifBlank { "Unknown" } }; td { +inspections.count { name in it.value.networkSettings?.networks.orEmpty() }.toString() } }
                    } }
                } }
            }
        }
        if (chosen != null) aside("topology-detail") {
            section("panel") {
                div("host-panel-heading") { div { serviceIcon(chosen.config?.image.orEmpty()); h2 { +(chosen.config?.labels?.get("com.docker.compose.service") ?: chosen.displayName()) }; p("hint") { +chosen.displayName() } }; pageLink("×", "$base&tab=topology", "btn") }
                nav("compose-tabs") { attributes["aria-label"] = "Selected container sections"
                    listOf("overview", "logs", "inspect", "stats", "config").forEach { tab ->
                        a(href = selection(base, selectedId, tab), classes = if (tab == detail) "active" else "") { attributes["hx-get"] = href; attributes["hx-target"] = "#main-content"; attributes["hx-push-url"] = "true"; if (tab == detail) attributes["aria-current"] = "page"; +tab.replaceFirstChar(Char::uppercase) }
                    }
                }
                when (detail) {
                    "logs" -> renderLiveStream("/containers/${selectedId.encodeURLPathPart()}/logs", "topology-logs", title = "Container logs")
                    "stats" -> if (chosen.state?.running == true) renderLiveStream("/containers/${selectedId.encodeURLPathPart()}/stats", "topology-stats", append = false, title = "Memory") else p("muted") { +"Statistics are available for running containers." }
                    "inspect" -> pre("topology-json") { +Json { prettyPrint = true }.encodeToString(ContainerInspectResponse.serializer(), chosen) }
                    "config" -> {
                        infoRow("Image", chosen.config?.image ?: "Unknown")
                        infoRow("Command", (listOfNotNull(chosen.path) + chosen.args.orEmpty()).joinToString(" "))
                        infoRow("Working directory", chosen.config?.workingDir ?: "Not specified")
                        details { summary { +"Environment" }; chosen.config?.env.orEmpty().forEach { p { code { +it } } } }
                    }
                    else -> {
                        infoRow("Image", chosen.config?.image ?: "Unknown")
                        infoRow("Container ID", selectedId.take(12), true)
                        infoRow("State", chosen.state?.status?.value ?: "Unknown")
                        infoRow("Health", chosen.state?.health?.status?.value ?: "No healthcheck reported")
                        infoRow("Restart policy", chosen.hostConfig?.restartPolicy?.name?.value ?: "Unknown")
                        chosen.networkSettings?.networks.orEmpty().forEach { (name, endpoint) ->
                            infoRow("Network", name)
                            infoRow("IP", listOfNotNull(endpoint.ipAddress?.takeIf { it.isNotBlank() }, endpoint.globalIPv6Address?.takeIf { it.isNotBlank() }).joinToString(", ").ifBlank { "Not assigned" })
                            infoRow("Aliases", endpoint.aliases.orEmpty().joinToString(", ").ifBlank { "None reported" })
                        }
                        infoRow("Container ports", chosen.ports().joinToString(", ").ifBlank { "None reported" })
                        pageLink("Open container details →", "/containers/${selectedId.encodeURLPathPart()}", "btn")
                    }
                }
            }
            if (detail == "overview") {
                section("panel") { h2 { +"Health check" }
                    val health = chosen.state?.health
                    p("hint") { +"${health?.status?.value ?: "No healthcheck reported"} · historical results may remain after stopping" }
                    health?.log?.lastOrNull()?.let { check -> infoRow("Last check", check.end ?: "Unknown"); infoRow("Exit code", check.exitCode?.toString() ?: "Unknown"); pre("topology-json") { +check.output.orEmpty() } }
                }
                section("panel") { h2 { +"Port bindings" }
                    val ports = bindings.filter { it.container == selectedId }
                    if (ports.isEmpty()) p("muted") { +"No host bindings. Exposed ports alone are not published." }
                    ports.forEach { infoRow("${it.address}:${it.hostPort}", "${it.port}${if (!it.active) " (configured)" else ""}") }
                }
                section("panel") { h2 { +"Volumes and mounts" }; if (chosen.mounts.isNullOrEmpty()) p("muted") { +"No mounts" }
                    chosen.mounts.orEmpty().forEach { infoRow(it.name ?: it.source ?: "Unknown source", "${it.destination ?: "Unknown destination"} · ${if (it.RW == true) "rw" else "ro"}") }
                }
            }
        } else aside("topology-detail panel") { h2 { +"Select a container" }; p("hint") { +"Open a card to inspect its networks, health, ports, mounts and logs." } }
    }
    topologyEdges()
}

private fun FlowContent.topologyNode(base: String, id: String, info: ContainerInspectResponse, network: String?, selected: Boolean) {
    val unhealthy = info.state?.health?.status?.value == "unhealthy"
    a(href = selection(base, id), classes = "topology-node${if (selected) " selected" else ""}${if (unhealthy) " unhealthy" else ""}") {
        attributes["hx-get"] = href; attributes["hx-target"] = "#main-content"; attributes["hx-push-url"] = "true"; attributes["data-node-container"] = id
        attributes["aria-label"] = "Select ${info.displayName()} in ${network ?: "no network"}"
        span("topology-node-title") { serviceIcon(info.config?.image.orEmpty()); strong { +(info.config?.labels?.get("com.docker.compose.service") ?: info.displayName()) } }
        small("topology-node-container") { attributes["title"] = info.displayName(); +info.displayName() }
        stateBadge(info.state?.status?.value ?: "unknown")
        info.state?.health?.status?.value?.let { health -> span(if (unhealthy) "topology-unhealthy" else "hint") { +health } }
        if (info.config?.labels?.get("com.docker.compose.oneoff")?.lowercase() == "true") small { +"One-off" }
        p("hint") { +(network?.let { info.networkSettings?.networks?.get(it)?.ipAddress?.takeIf { it.isNotBlank() } } ?: "IP not assigned") }
        div("topology-port-tags") { info.ports().forEach { span { +it } } }
    }
}

private fun FlowContent.topologyEdges() {
    script { unsafe { +"""
    (function(){
        const canvas=document.getElementById('compose-topology'); if(!canvas) return;
        const svg=canvas.querySelector('.topology-edges');
        function draw(){
            const box=canvas.getBoundingClientRect(); svg.setAttribute('viewBox','0 0 '+box.width+' '+box.height); svg.replaceChildren();
            canvas.querySelectorAll('.topology-network').forEach(network=>{
                const nodes=Array.from(network.querySelectorAll('[data-node-container]'));
                if(!nodes.length) return;
                const rects=nodes.map(n=>n.getBoundingClientRect());
                const top=Math.min(...rects.map(r=>r.top));
                const first=rects.filter(r=>Math.abs(r.top-top)<5);
                const y=Math.max(...first.map(r=>r.bottom))-box.top+22;
                const xs=rects.map(r=>r.left+r.width/2-box.left);
                const bus=document.createElementNS('http://www.w3.org/2000/svg','path');
                bus.setAttribute('class','network-bus');
                let d='M '+Math.min(...xs)+' '+y+' H '+Math.max(...xs);
                rects.forEach(r=>{const x=r.left+r.width/2-box.left; const edge=Math.abs(r.top-top)<5?r.bottom-box.top:r.top-box.top;d+=' M '+x+' '+edge+' V '+y;});
                bus.setAttribute('d',d);svg.appendChild(bus);
            });
            canvas.querySelectorAll('[data-binding-container]').forEach(binding=>{
                const nodes=Array.from(canvas.querySelectorAll('[data-node-container]')).filter(n=>n.dataset.nodeContainer===binding.dataset.bindingContainer);
                nodes.forEach(node=>{
                    const a=binding.getBoundingClientRect(), b=node.getBoundingClientRect();
                    const x=a.left+a.width/2-box.left,y=a.bottom-box.top,tx=b.left+b.width/2-box.left,ty=b.top-box.top;
                    const path=document.createElementNS('http://www.w3.org/2000/svg','path');
                    path.setAttribute('d','M '+x+' '+y+' C '+x+' '+(y+24)+', '+tx+' '+(ty-24)+', '+tx+' '+ty);
                    path.setAttribute('class',binding.className.replace('topology-binding','')+(binding.dataset.bindingActive==='true'?'':' configured'));
                    svg.appendChild(path);
                });
            });
        }
        const observer=new ResizeObserver(draw); observer.observe(canvas); draw();
        function cleanup(event){
            if(event.type==='htmx:beforeCleanupElement' && event.detail.elt!==canvas && !event.detail.elt.contains(canvas)) return;
            observer.disconnect(); document.removeEventListener('htmx:beforeCleanupElement',cleanup); window.removeEventListener('pagehide',cleanup);
        }
        document.addEventListener('htmx:beforeCleanupElement',cleanup); window.addEventListener('pagehide',cleanup);
    })();
    """.trimIndent() } }
}
