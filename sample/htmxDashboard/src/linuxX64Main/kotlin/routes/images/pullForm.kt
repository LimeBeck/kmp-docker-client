package routes.images

import kotlinx.html.*

fun FlowContent.renderPullForm() {
    div("mb-6 bg-gray-800 p-4 rounded border border-gray-700") {
        id = "image-pull-panel"
        form {
            input(type = InputType.text, name = "image-pull-name", classes = "bg-gray-700 p-2 rounded mr-3") {
                placeholder = "e.g. alpine:latest"; required = true
            }
            button(type = ButtonType.submit, classes = "bg-blue-600 p-2 rounded mr-3") { +"Pull image" }
            button(type = ButtonType.button, classes = "bg-gray-700 p-2 rounded") { attributes["data-cancel"] = ""; +"Cancel pull" }
        }
        p("text-sm text-gray-400 my-2") { +"Cancellation closes the request. Docker may finish work in the background; refresh images before retrying." }
        pre("max-h-64 overflow-auto whitespace-pre-wrap text-sm") {
            attributes["aria-live"] = "polite"; attributes["data-output"] = ""
        }
        a(href = "/images", classes = "text-blue-400") { +"Refresh images" }
        script { unsafe { +"""
            (function() {
                const panel = document.getElementById('image-pull-panel');
                const form = panel.querySelector('form');
                const output = panel.querySelector('[data-output]');
                const submit = form.querySelector('[type=submit]');
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
                    submit.disabled = true;
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
                                if (message.state === 'success' || message.state === 'error') finalState = true;
                            }
                            if (part.done) break;
                        }
                        if (!finalState || pending.trim()) throw new Error('Progress stream ended without a final result');
                    } catch (error) {
                        if (!disposed) line(error.name === 'AbortError' ? 'Cancelled. Refresh images to reconcile Docker state.' : 'Failed: ' + error.message);
                    } finally {
                        controller?.abort();
                        controller = null;
                        if (!disposed) submit.disabled = false;
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
