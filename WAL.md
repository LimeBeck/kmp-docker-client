# WAL (Write-Ahead Log)

## Current Focus
- Completed the terminal/session portion of 0.1 on `codex/terminal-session-lifecycle`.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#exec.streams`; target remains the single-host panel in FEAT-002.
- Next release target is 0.1.0 (`gradle.properties`), as requested by the owner. Published baseline remains 0.0.10; no tag or publication was created.

## Completed in Last Session
- Raised the default build version from snapshot to 0.1.0, updated README and `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#publish.development`. The version bump is included in MR #4. `:lib:properties` verified both version and libVersion are 0.1.0; `git diff --check` passed.
- Added `ExecSession.incomingChunks`: immediate binary TTY output, bounded multiplex parsing, stdout/stderr identity, and preservation of HTTP-upgrade leftover bytes.
- Sessions allow one output collector and close on EOF, consumer failure, cancellation, or explicit close. Handshake cancellation propagates. Prefix forwarding has collection scope.
- Preserved the line API and existing constructors/startInteractive calls; added explicit non-TTY exec mode. README explains single-collection ownership and migration from the removed unscoped prependLeftover utility.
- JVM uses independent SocketChannel read/write operations. Node.js pauses incoming data and awaits write callbacks. Linux uses shutdown, idempotent close, and defers descriptor release until I/O jobs finish.
- Dashboard WebSockets use the session byte stream and scoped cleanup, including failed container starts.
- Added common parser/lifecycle tests, a real-Docker no-newline prompt/input test on all platforms, and JVM Unix-socket tests for duplex I/O, handshake cancellation, and repeated disconnects.
- Final tests passed: 161 executions (71 JVM, 45 Node.js, 45 Linux), including the 20-session disconnect regression. Final `build :lib:dokkaGenerateHtml` passed, including debug/release sample executables.

## Next Steps
1. Review and merge MR #4. Publish 0.1.0 via a new release tag when requested; broader 1.0 readiness work remains.
2. Address logs/stats/events reconnect and bounded-resource behavior, then expose image-operation progress.
3. Resolve remaining Kotlin, Gradle/plugin, and CI deprecations at their source, per `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#maintenance.deprecations`.

## Known Risks / Constraints
- API 1.51 is fixed; no version negotiation. Docker 28.5.2 is provisioned in release CI.
- incoming.first()/take() now closes the session; reuse requires one long-lived collector. Binary chunks may split UTF-8; use a streaming decoder.
- Old line-oriented non-TTY logs still allocate a whole frame; the bounded byte parser applies to incomingChunks. Broader log-stream hardening is follow-up work.
- Three readUTF8Line deprecations remain in DockerClient, Containers stats, and System events. The readLogLines call now uses readLine.
- Authentication/roles/audit belong to the application; sample still binds to loopback.
- The review report is local and excluded via .git/info/exclude; never commit it.
- Do not move v0.0.9/v0.0.10 or re-upload the published Maven version. v0.0.10 publication and Central artifacts were verified in the previous session.

## Decisions Pending
- Supported Docker/platform matrix and API compatibility gates for 1.0.0.

## Resume Commands
- `git diff --check`
- `./gradlew :lib:jvmTest --tests '*TerminalSessionTest' --tests '*DockerHttpRegressionTest' --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --console=plain --max-workers=2`
