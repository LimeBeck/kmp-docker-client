package ui

import kotlinx.html.*

/** A bounded stream view whose connection belongs to this DOM element. */
fun FlowContent.renderLiveStream(path: String, elementId: String, append: Boolean = true) {
    div("bg-black rounded-lg p-4 font-mono text-xs max-h-80 overflow-y-auto border border-gray-700") {
        id = elementId
        attributes["data-stream-url"] = path
        attributes["data-append"] = append.toString()
        div("text-gray-400") { attributes["data-stream-status"] = ""; +"Connecting…" }
        div { attributes["data-stream-output"] = "" }
        script {
            unsafe { +"""
                (function() {
                    const panel = document.getElementById('$elementId');
                    const status = panel.querySelector('[data-stream-status]');
                    const output = panel.querySelector('[data-stream-output]');
                    const stream = new EventSource(panel.dataset.streamUrl);
                    let disposed = false;
                    stream.onopen = function() { status.textContent = 'Connected'; };
                    stream.onmessage = function(event) {
                        if (disposed) return;
                        if (panel.dataset.append !== 'true') output.replaceChildren();
                        const row = document.createElement('div');
                        row.innerHTML = event.data;
                        output.appendChild(row);
                        while (output.children.length > 200) output.firstElementChild.remove();
                        panel.scrollTop = panel.scrollHeight;
                    };
                    stream.addEventListener('done', function() { stream.close(); status.textContent = 'Stream ended. Refresh to reconnect.'; });
                    stream.onerror = function() { stream.close(); status.textContent = 'Disconnected. Refresh to reconnect.'; };
                    function cleanup(event) {
                        if (event.type === 'htmx:beforeCleanupElement' &&
                            event.detail.elt !== panel && !event.detail.elt.contains(panel)) return;
                        if (disposed) return;
                        disposed = true;
                        stream.close();
                        window.removeEventListener('pagehide', cleanup);
                        document.removeEventListener('htmx:beforeCleanupElement', cleanup);
                    }
                    window.addEventListener('pagehide', cleanup);
                    document.addEventListener('htmx:beforeCleanupElement', cleanup);
                })();
            """.trimIndent() }
        }
    }
}
