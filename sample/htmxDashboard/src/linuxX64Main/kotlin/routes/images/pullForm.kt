package routes.images

import kotlinx.html.*

fun FlowContent.renderPullForm(embedded: Boolean = false) {
    div("panel stack") {
        id = "image-pull-panel"
        form(classes = "toolbar") {
            label("field") { +"Image name"
            input(type = InputType.text, name = "image-pull-name", classes = "") {
                placeholder = "e.g. alpine:latest"; required = true
            }
            }
            button(type = ButtonType.submit, classes = "btn btn-primary") { +"Pull image" }
            button(type = ButtonType.button, classes = "btn") { disabled = true; attributes["data-cancel"] = ""; +"Cancel pull" }
        }
        p("hint") { +"Cancellation closes the request. Docker may finish work in the background; refresh images before retrying." }
        pre("stream-output") {
            attributes["aria-label"] = "Image pull progress"; attributes["data-output"] = ""
        }
        p("status") { attributes["data-pull-status"] = ""; attributes["role"] = "status"; +"Ready to pull" }
        if (embedded) p("hint") { +"After a successful pull, continue configuring the container above." }
        else a(href = "/images") { +"Refresh images" }
        script { unsafe { +"""
            (function() {
                const panel = document.getElementById('image-pull-panel');
                const form = panel.querySelector('form');
                const output = panel.querySelector('[data-output]');
                const submit = form.querySelector('[type=submit]');
                const cancel = panel.querySelector('[data-cancel]');
                const status = panel.querySelector('[data-pull-status]');
                let controller = null;
                let disposed = false;
                function line(text) {
                    output.textContent = (output.textContent + text + '\n').split('\n').slice(-200).join('\n');
                    output.scrollTop = output.scrollHeight;
                }
                form.onsubmit = async function(event) {
                    event.preventDefault();
                    if (controller) return;
                    controller = new AbortController();
                    submit.disabled = true; cancel.disabled = false; status.textContent = 'Pulling…';
                    output.textContent = '';
                    let finalState = false;
                    try {
                        const response = await fetch('/images/pull', {
                            method: 'POST', body: new URLSearchParams(new FormData(form)), signal: controller.signal
                        });
                        if (!response.ok) throw new Error('HTTP ' + response.status);
                        const reader = response.body.getReader();
                        const decoder = new TextDecoder();
                        let pending = '';
                        while (true) {
                            const part = await reader.read();
                            pending += decoder.decode(part.value || new Uint8Array(), {stream: !part.done});
                            if (pending.length > 65536) throw new Error('Progress record too large');
                            const records = pending.split('\n');
                            pending = records.pop();
                            for (const record of records) {
                                if (!record) continue;
                                const message = JSON.parse(record);
                                if (message.state !== 'heartbeat') line(message.message);
                                if (message.state === 'success' || message.state === 'error') { finalState = true; status.textContent = message.message; status.className = 'status ' + (message.state === 'success' ? 'status-running' : 'status-error'); }
                            }
                            if (part.done) break;
                        }
                        if (!finalState || pending.trim()) throw new Error('Progress stream ended without a final result');
                    } catch (error) {
                        if (!disposed) { const message = error.name === 'AbortError' ? 'Cancelled. Refresh images to reconcile Docker state.' : 'Failed: ' + error.message; line(message); status.textContent = message; status.className = 'status status-error'; }
                    } finally {
                        controller?.abort();
                        controller = null;
                        if (!disposed) { submit.disabled = false; cancel.disabled = true; }
                    }
                };
                panel.querySelector('[data-cancel]').onclick = function() { controller?.abort(); };
                function cleanup(event) {
                    if (event.type === 'htmx:beforeCleanupElement' &&
                        event.detail.elt !== panel && !event.detail.elt.contains(panel)) return;
                    if (disposed) return;
                    disposed = true;
                    controller?.abort();
                    window.removeEventListener('pagehide', cleanup);
                    document.removeEventListener('htmx:beforeCleanupElement', cleanup);
                }
                window.addEventListener('pagehide', cleanup);
                document.addEventListener('htmx:beforeCleanupElement', cleanup);
            })();
        """.trimIndent() } }
    }
}
