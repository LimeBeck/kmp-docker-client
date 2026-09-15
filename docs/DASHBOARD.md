# Dashboard example

The bundled htmx dashboard demonstrates single-host SDK workflows. Its UI behavior is not an SDK release gate. It remains a development example bound to loopback; user authentication, roles, credential storage and audit are application concerns. It does not implement Compose.

## Run

```sh
./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2
./sample/htmxDashboard/build/bin/linuxX64/debugExecutable/htmxDashboard.kexe /var/run/docker.sock 8080
```

Both arguments are optional. The defaults are `/var/run/docker.sock` and port `8080`; the HTTP host remains `127.0.0.1`. A separate socket and port allow testing without changing the user's normal Docker daemon or dashboard instance.

## Download a compiled executable

Successful Release CI builds and the Docker 28.5.2/JDK 21 cell of PR CI upload an
`htmx-dashboard-linux-x64` artifact. Open the workflow run in GitHub Actions,
download it from **Artifacts**, and extract `htmxDashboard.kexe`.

The executable targets Linux X64. Artifact extraction does not preserve executable
permission, so restore it before launching:

```sh
chmod +x htmxDashboard.kexe
./htmxDashboard.kexe /var/run/docker.sock 8080
```

The same socket permissions, arguments and loopback binding described above apply.
The binary is a workflow artifact, not an attachment to the GitHub Release.

## Interface

The dashboard opens on Containers. A sidebar links to Images, Volumes, Networks and System. The sun/moon button switches between Graphite and Light; only that preference is saved in browser storage.

- Lists provide name/ID/image search, sorting and container-state filtering. Search and filters live in the URL, work with browser history and are retained when returning from details within the current page session.
- Container details have Overview, Logs, Terminal and Configuration sections. Overview shows the latest memory sample with readable units and usage percentage, not historical metrics.
- Create and Recreate have separate forms. Environment, ports and volumes use repeatable entries. Validation preserves entered values and marks the relevant field; a failed operation does not clear the current view.
- An embedded image-pull form lets you download a missing image while keeping configuration inputs in memory. Navigation or reload discards those inputs. HTMX history caching is disabled and rendered pages use `Cache-Control: no-store`, so configuration is not deliberately persisted in browser storage.
- Destructive actions describe their scope before running. Deleting a volume requires its exact name. Image prune uses Docker's dangling-image default; volume prune uses the anonymous-volume default. Errors are displayed and success is reported after the operation finishes.
- Below 640 px, resource tables become labeled cards with all fields and actions visible; search spans the width and state/sort controls sit alongside each other. On larger screens, dense tables scroll within their own region only when necessary. Navigation, forms, notifications and confirmation dialogs support keyboard interaction and visible focus.

## Container workflow

The create form accepts an image, one command argument per line, environment entries, loopback-published ports, named-volume mounts and an existing network. Whitespace within command arguments is preserved; a wholly blank command field uses the image default. The initial defaults remain `/bin/sh` and interactive TTY. Pull the image first, using the embedded pull panel if needed. Containers created through the form are marked as managed by the sample.

Recreation is restricted to sample-managed containers because the form intentionally exposes only this configuration subset. It is not a generic clone of arbitrary Docker inspect output. Recreating an unrelated container could otherwise silently lose settings the form does not understand.

1. Prepare a candidate under a separate name. A missing image or invalid create configuration leaves the original running.
2. Stop the original and start the candidate. If startup fails, remove the candidate and restore the original's previous running state. If rollback itself fails, report that it needs attention.
3. Inspect the candidate and verify application readiness and data. Running state alone is not a readiness check. The old container remains stopped until the user chooses a result.
4. Confirm the replacement to delete the stopped original, or roll back to restore it and remove the candidate. The candidate keeps its distinct name after confirmation. Another replacement can then be prepared.

The server serializes replacement transitions and rejects a second pending candidate for the same original. It does not provide distributed orchestration or transactions across multiple dashboard instances. Desired state is represented by the Docker containers and their labels; the UI can discover a pending replacement after navigation or dashboard restart.

Container deletion, replacement confirmation and rollback never delete named volumes. Volume deletion is a separate explicit operation. **Rollback does not undo writes already made to a shared volume.** Application backups, data migrations and readiness policy remain the caller's responsibility.

## Streams and terminal

- Logs and events keep at most 200 displayed records. Logs remain available for stopped containers as a finite last-200-record view. Live logs follow new output until the user scrolls away or turns Follow off. Stats replace the latest displayed memory sample. The example does not claim a complete metrics dashboard.
- Each view closes its EventSource when leaving the page or when HTMX removes the view. A write heartbeat also detects an idle HTTP disconnect and cancels the corresponding Docker subscription; writes are serialized.
- Disconnected/finished live views show their state. Reconnect replaces only that subscription and starts a fresh bounded view; it does not replay missed records. Automatic replay/deduplication is not implemented by this sample; see [application recovery policy](STREAM-RECOVERY.md).
- Terminal exec and attach carry binary UTF-8 bytes, resize messages and explicit cleanup. An empty Exec field opens `/bin/sh`. Fullscreen/Escape and ResizeObserver-driven fitting update the Docker TTY size.
- Pull displays typed progress and the operation's final result. Cancelling the request or navigating away aborts the HTTP reader. Heartbeats release idle upstream requests too. Cancellation cannot guarantee Docker-side rollback: refresh Images before retrying.

## Optional manual smoke check

After starting the example, optionally create a disposable container, open its terminal and live views, and inspect image progress. Use only disposable resources and explicitly clean them up. These checks are for sample development; PR and release CI validate SDK behavior through Kotlin tests against Docker, without dashboard HTTP/WebSocket or browser acceptance.

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance`.

## Dashboard checks

Build the sample independently of library release tasks:

```sh
./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2
npm ci --prefix sample/htmxDashboard/tests --ignore-scripts
npm test --prefix sample/htmxDashboard/tests
```

The DOM tests cover filtering/history links, dialog confirmation, overlapping requests, form serialization, theme switching and bounded stream cleanup. They do not replace visual/browser checks.

An opt-in HTTP smoke test uses an already-running dashboard and locally available `alpine:latest`. It creates uniquely named disposable containers and a volume, exercises replacement/rollback and stopped logs, and removes only its own resources; it never calls prune:

```sh
python3 sample/htmxDashboard/tests/http_smoke.py http://127.0.0.1:18080
```

The dashboard's HTMX/xterm assets are still loaded from CDNs. Its application styles and interaction code are embedded in the Native executable; no separate asset directory or frontend bundler is needed.
