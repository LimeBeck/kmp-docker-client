package ui

import kotlinx.html.*

fun FlowContent.renderTerminalPanel(socketPath: String) {
    div("bg-gray-800 rounded-lg border border-gray-700 p-4") {
        id = "terminal-panel"
        style = "display:flex; flex-direction:column; height:65vh; min-height:280px; overflow:hidden;"
        attributes["data-socket-path"] = socketPath
        div("flex justify-end mb-2") {
            button(classes = "px-3 py-1 rounded bg-gray-700 hover:bg-gray-600") {
                id = "terminal-fullscreen"
                type = ButtonType.button
                attributes["aria-pressed"] = "false"
                +"Full screen"
            }
        }
        div {
            id = "terminal"
            style = "flex:1; min-height:0; min-width:0; overflow:hidden;"
        }
        script {
            unsafe {
                +"""
                (function() {
                    const panel = document.getElementById('terminal-panel');
                    const host = panel.querySelector('#terminal');
                    const button = panel.querySelector('#terminal-fullscreen');
                    const term = new Terminal({cursorBlink: true, theme: {background: '#1f2937'}});
                    const fit = new FitAddon.FitAddon();
                    term.loadAddon(fit);
                    term.open(host);
                    const scheme = location.protocol === 'https:' ? 'wss://' : 'ws://';
                    const socket = new WebSocket(scheme + location.host + panel.dataset.socketPath);
                    socket.binaryType = 'arraybuffer';
                    const encoder = new TextEncoder();
                    let disposed = false;
                    let frame = 0;
                    let lastSize = '';
                    let expanded = false;
                    function fitTerminal() {
                        frame = 0;
                        if (disposed || !host.clientWidth || !host.clientHeight) return;
                        fit.fit();
                        const size = term.cols + ':' + term.rows;
                        if (socket.readyState === WebSocket.OPEN && size !== lastSize) {
                            socket.send(JSON.stringify({type: 'resize', cols: term.cols, rows: term.rows}));
                            lastSize = size;
                        }
                    }
                    function scheduleFit() {
                        if (!disposed && !frame) frame = requestAnimationFrame(fitTerminal);
                    }
                    const observer = new ResizeObserver(scheduleFit);
                    observer.observe(host);
                    window.addEventListener('resize', scheduleFit);
                    function fullscreenChanged() {
                        const active = expanded || document.fullscreenElement === panel;
                        panel.style.height = active ? '100dvh' : '65vh';
                        panel.style.margin = active ? '0' : '';
                        panel.style.position = expanded ? 'fixed' : '';
                        panel.style.inset = expanded ? '0' : '';
                        panel.style.zIndex = expanded ? '100' : '';
                        button.textContent = active ? 'Exit full screen' : 'Full screen';
                        button.setAttribute('aria-pressed', String(active));
                        scheduleFit();
                        term.focus();
                    }
                    document.addEventListener('fullscreenchange', fullscreenChanged);
                    button.onclick = async function() {
                        if (expanded) {
                            expanded = false;
                            fullscreenChanged();
                        } else if (document.fullscreenElement === panel) {
                            await document.exitFullscreen();
                        } else {
                            try {
                                await panel.requestFullscreen();
                            } catch (error) {
                                if (disposed) return;
                                expanded = true;
                                fullscreenChanged();
                            }
                        }
                    };
                    function escapeFullscreen(event) {
                        if (event.key === 'Escape' && (expanded || document.fullscreenElement === panel)) {
                            event.preventDefault();
                            event.stopPropagation();
                            if (expanded) {
                                expanded = false;
                                fullscreenChanged();
                            } else {
                                document.exitFullscreen().catch(function() {});
                            }
                        }
                    }
                    document.addEventListener('keydown', escapeFullscreen, true);
                    socket.onopen = function() { scheduleFit(); term.focus(); };
                    socket.onmessage = function(event) {
                        if (!disposed) term.write(typeof event.data === 'string' ? event.data : new Uint8Array(event.data));
                    };
                    socket.onclose = function() {
                        if (!disposed) term.write('\r\n\x1b[31mConnection closed.\x1b[0m\r\n');
                    };
                    const input = term.onData(function(data) {
                        if (socket.readyState === WebSocket.OPEN) socket.send(encoder.encode(data));
                    });
                    function cleanup(event) {
                        if (event.type === 'htmx:beforeCleanupElement' &&
                            event.detail.elt !== panel && !event.detail.elt.contains(panel)) return;
                        if (disposed) return;
                        disposed = true;
                        cancelAnimationFrame(frame);
                        observer.disconnect();
                        window.removeEventListener('resize', scheduleFit);
                        window.removeEventListener('pagehide', cleanup);
                        document.removeEventListener('fullscreenchange', fullscreenChanged);
                        document.removeEventListener('keydown', escapeFullscreen, true);
                        document.removeEventListener('htmx:beforeCleanupElement', cleanup);
                        if (document.fullscreenElement === panel) document.exitFullscreen().catch(function() {});
                        socket.close();
                        input.dispose();
                        term.dispose();
                    }
                    document.addEventListener('htmx:beforeCleanupElement', cleanup);
                    window.addEventListener('pagehide', cleanup);
                    if (document.fonts) document.fonts.ready.then(scheduleFit);
                    scheduleFit();
                })();
                """.trimIndent()
            }
        }
    }
}
