package ui

import kotlinx.html.*

/** A bounded stream view whose connection belongs to this DOM element. */
fun FlowContent.renderLiveStream(path: String, elementId: String, append: Boolean = true, title: String = "Live events") {
    section("panel") {
        id = elementId; attributes["data-stream"] = ""; attributes["data-stream-url"] = path; attributes["data-append"] = append.toString()
        div("stream-head") {
            div { h2 { +title }; span("status") { attributes["data-stream-status"] = ""; attributes["role"] = "status"; +"Connecting…" } }
            div("actions") {
                if (append) button(type = ButtonType.button, classes = "btn btn-small") { attributes["data-follow"] = ""; attributes["aria-pressed"] = "true"; +"Follow" }
                button(type = ButtonType.button, classes = "btn btn-small") { attributes["data-reconnect"] = ""; +"Reconnect" }
            }
        }
        div("stream-output") { attributes["data-stream-output"] = ""; attributes["tabindex"] = "0"; attributes["aria-label"] = "$title output" }
        p("stream-foot") { +(if (append) "Last 200 records. Reconnecting starts a fresh view; some records may be missed." else "Latest sample only. A disconnected value may be out of date.") }
        script { unsafe { +"""
        (function() {
            const panel = document.getElementById('$elementId');
            const status = panel.querySelector('[data-stream-status]');
            const output = panel.querySelector('[data-stream-output]');
            const follow = panel.querySelector('[data-follow]');
            const reconnect = panel.querySelector('[data-reconnect]');
            let stream = null, disposed = false, following = true;
            function setFollowing(value) { following = value; if (follow) follow.setAttribute('aria-pressed',String(value)); }
            function state(text, live) { status.textContent = text; status.className = 'status' + (live ? ' status-running' : ''); }
            function connect() {
                if (disposed) return;
                if (stream) stream.close();
                output.replaceChildren(); state('Connecting…',false);
                const connection = new EventSource(panel.dataset.streamUrl); stream = connection;
                connection.onopen = function() { if (stream === connection) state('Connected',true); };
                connection.onmessage = function(event) {
                    if (disposed || stream !== connection) return;
                    const wasAtBottom = output.scrollHeight - output.clientHeight - output.scrollTop < 24;
                    if (panel.dataset.append !== 'true') output.replaceChildren();
                    const row = document.createElement('div'); row.innerHTML = event.data; output.appendChild(row);
                    let removedHeight = 0;
                    while (output.children.length > 200) { removedHeight += output.firstElementChild.getBoundingClientRect().height; output.firstElementChild.remove(); }
                    if (following && wasAtBottom) output.scrollTop = output.scrollHeight;
                    else if (removedHeight) output.scrollTop = Math.max(0,output.scrollTop - removedHeight);
                };
                connection.addEventListener('done', function() { if (stream !== connection) return; connection.close(); state('Stream ended',false); });
                connection.addEventListener('failure', function(event) { if (stream !== connection) return; connection.close(); state(event.data || 'Disconnected',false); });
                connection.onerror = function() { if (stream !== connection) return; connection.close(); state('Disconnected · reconnect to retry',false); };
            }
            if (follow) follow.onclick = function() { setFollowing(!following); if (following) output.scrollTop = output.scrollHeight; };
            output.addEventListener('scroll',function() { if (output.scrollHeight-output.clientHeight-output.scrollTop > 24) setFollowing(false); });
            reconnect.onclick = connect;
            function cleanup(event) {
                if (event.type === 'htmx:beforeCleanupElement' && event.detail.elt !== panel && !event.detail.elt.contains(panel)) return;
                if (disposed) return; disposed = true; if (stream) stream.close();
                window.removeEventListener('pagehide',cleanup); document.removeEventListener('htmx:beforeCleanupElement',cleanup); panel.removeEventListener('dashboard:dispose',cleanup);
            }
            window.addEventListener('pagehide',cleanup); document.addEventListener('htmx:beforeCleanupElement',cleanup); panel.addEventListener('dashboard:dispose',cleanup);
            connect();
        })();
        """.trimIndent() } }
    }
}
