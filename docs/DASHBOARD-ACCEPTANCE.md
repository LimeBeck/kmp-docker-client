# Dashboard acceptance example

The bundled htmx dashboard demonstrates the single-host SDK workflows used for RC acceptance. It remains a development example bound to loopback; user authentication, roles, credential storage and audit are application concerns. It does not implement Compose.

## Run

```sh
./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2
./sample/htmxDashboard/build/bin/linuxX64/debugExecutable/htmxDashboard.kexe /var/run/docker.sock 8080
```

Both arguments are optional. The defaults are `/var/run/docker.sock` and port `8080`; the HTTP host remains `127.0.0.1`. A separate socket and port allow testing without changing the user's normal Docker daemon or dashboard instance.

## Container workflow

The create form accepts an image, one command argument per line, environment entries, loopback-published ports, named-volume mounts and an existing network. Empty command arguments use the image default. Pull the image first. Containers created through the form are marked as managed by the sample.

Recreation is restricted to sample-managed containers because the form intentionally exposes only this configuration subset. It is not a generic clone of arbitrary Docker inspect output. Recreating an unrelated container could otherwise silently lose settings the form does not understand.

1. Prepare a candidate under a separate name. A missing image or invalid create configuration leaves the original running.
2. Stop the original and start the candidate. If startup fails, remove the candidate and restore the original's previous running state. If rollback itself fails, report that it needs attention.
3. Inspect the candidate and verify application readiness and data. Running state alone is not a readiness check. The old container remains stopped until the user chooses a result.
4. Confirm the replacement to delete the stopped original, or roll back to restore it and remove the candidate. The candidate keeps its distinct name after confirmation. Another replacement can then be prepared.

The server serializes replacement transitions and rejects a second pending candidate for the same original. It does not provide distributed orchestration or transactions across multiple dashboard instances. Desired state is represented by the Docker containers and their labels; the UI can discover a pending replacement after navigation or dashboard restart.

Container deletion, replacement confirmation and rollback never delete named volumes. Volume deletion is a separate explicit operation. **Rollback does not undo writes already made to a shared volume.** Application backups, data migrations and readiness policy remain the caller's responsibility.

## Streams and terminal

- Logs and events keep at most 200 displayed records. Stats replace the latest displayed memory sample. The example does not claim a complete metrics dashboard.
- Each view closes its EventSource when leaving the page or when HTMX removes the view. A write heartbeat also detects an idle HTTP disconnect and cancels the corresponding Docker subscription; writes are serialized.
- Disconnected/finished live views show their state and require refresh to reconnect. Automatic replay/deduplication is not implemented by this sample; see [application recovery policy](STREAM-RECOVERY.md).
- Terminal exec and attach carry binary UTF-8 bytes, resize messages and explicit cleanup. An empty Exec field opens `/bin/sh`. Fullscreen/Escape and ResizeObserver-driven fitting update the Docker TTY size.
- Pull displays typed progress and the operation's final result. Cancelling the request or navigating away aborts the HTTP reader. Heartbeats release idle upstream requests too. Cancellation cannot guarantee Docker-side rollback: refresh Images before retrying.

## Reproducible application test

After building the debug executable:

```sh
scripts/with-isolated-docker.sh scripts/run-dashboard-acceptance.sh
```

The harness creates a labelled temporary Docker 29 daemon and removes its data volume at exit. The application runner selects a free loopback port, starts the real native dashboard, preloads an Alpine shell and BusyBox HTTP fixture, and runs `scripts/dashboard-acceptance.py` using Python's standard library. No browser automation dependency is required for the HTTP/WebSocket suite.

The suite operates through the dashboard's HTTP/WebSocket routes. Docker CLI reads independently verify configuration, actual HTTP reachability and retained data; CLI writes are limited to fixture setup, a stimulus event and cleanup. Coverage includes:

- create, published port, environment, named volume and network attachment;
- exec/attach prompts, UTF-8 and `stty size` through the actual WebSocket bridge;
- logs, stats and events;
- missing-image prepare failure, startup failure and restoration of the original;
- candidate rollback, confirmation, and reading retained data after deleting both predecessors;
- successful pull progress, registry failure, missing resource and cancelling an idle registry request;
- repeated active and idle stream/session cleanup, with bounded native process socket counts after warm-up.

The slow registry fixtures are loopback listeners inside the disposable daemon's network namespace. They accept repeated connections without replying, so cancellation does not depend on public network timeouts. Test deadlines fail the run; the surrounding harness still removes the whole temporary daemon on error.

Logs are retained under `sample/htmxDashboard/build/acceptance/`. PR CI runs this application suite in the Docker 29/JDK 21 cell; release CI runs it before publication. Browser-only presentation checks remain separately recorded in [RC acceptance](RC-ACCEPTANCE.md).

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance`.
