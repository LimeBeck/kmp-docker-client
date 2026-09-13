# Recovering after Docker restarts

The application owns reconnection and reconciliation. The SDK does not silently retry a request, reconnect a terminal, or replay container mutations.

## Verified behavior

`scripts/with-isolated-docker.sh ./gradlew :lib:daemonRestartTest --warning-mode=fail --console=plain --max-workers=2` creates a temporary Docker 29.0.0 daemon, using a separate Unix socket and data volume. It requires Docker with permission to start a privileged DinD container. The harness restarts and removes only its labelled temporary container; it never restarts the host daemon. The fixture image is Alpine.

The JVM acceptance test establishes logs, stats, events and an interactive exec session before restarting the temporary daemon. It waits for old subscriptions to terminate before cancelling anything, reconnects through the same SDK client, reconciles the container list, resumes logs/stats/events and opens repeated terminal sessions. Timeouts fail the test. The test is deliberately excluded from ordinary jvmTest and runs through the explicit daemonRestartTest task; PR CI and release CI invoke the harness.

Local evidence on 2026-09-12: all four old subscriptions completed with EOF; recovery passed; process socket counts were 2 before and 3 after ten further terminal sessions (allowed bounded pool variation: two sockets). This is a JVM/Docker 29 restart check, not a claim that every runtime or every daemon failure mode has been tested. The normal compatibility matrix covers streams and cancellation on JVM, Node.js and Linux X64.

## Application policy

1. Treat both unexpected EOF and transport failure as a disconnected live subscription. Intentional completion of a finite query is different: it should not start a retry loop.
2. Close old sessions and finish their collectors. Never run overlapping retry loops for the same subscription. Propagate caller cancellation and consumer exceptions instead of interpreting them as transport failures.
3. Retry connection establishment with a bounded exponential delay, jitter and an application deadline. Show disconnected/reconnecting state. Do not automatically retry authentication or validation errors, or mutations such as create/remove.
4. Once the daemon is available, refresh current resources. Events are finite history, and restarting Docker may lose that history; replay alone cannot reconstruct authoritative state.
5. For events, retain a timestamp, request an overlap using `since`, and deduplicate by event timestamp/type/action/actor. Bound the deduplication cache. Reconcile again if the history window was exceeded.
6. For logs, choose an explicit `since`/`tail` policy and bound the display buffer. Replaying overlapping timestamps may produce duplicates; the application must choose how to present them. Stats can begin a fresh subscription without replay.
7. A terminal belongs to its original exec/attach session. Show its disconnection and require opening a new session; do not replay typed commands. A disconnected terminal does not prove the process it ran has stopped.
8. Cancelling image pull/push/load releases the connection but does not guarantee daemon-side rollback. Refresh image/resource state before deciding whether to retry.

The acceptance test demonstrates termination and explicit resubscription; it does not provide a reusable automatic recovery coordinator. UI retry/backoff, event deduplication and state reconciliation remain application responsibilities.

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance`.
