# WAL (Write-Ahead Log)

## Current Focus
- Version 0.1.0 in MR #4 (`codex/terminal-session-lifecycle`): terminal/session ownership plus reliable logs/stats/events.
- Published baseline remains 0.0.10; no new release tag or publication has been created.
- Contracts: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#exec.streams` and `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.streams.recovery`.

## Completed in Last Session
- Added a shared Terminal/Exec panel with ResizeObserver fitting, initial size synchronization, deduplicated Docker TTY resize messages, fullscreen and viewport fallback. Escape and the exit button restore the panel. Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#exec.dashboard-sizing`.
- WebSocket text frames now carry bounded resize dimensions; binary UTF-8 frames carry shell input. Both attach and exec forward sizes to their own Docker endpoint; non-TTY attach skips resizing.
- Navigation cleans up xterm, socket, observer, animation frame and event listeners. Binary output uses ArrayBuffer to preserve ordering without asynchronous FileReader callbacks.
- Fixed attach to already-running containers: avoid a redundant start and tolerate a concurrent successful start, instead of closing the session on Docker HTTP 304.
- Updated shared UI/routes, README and PROP-001. Real Docker WebSocket checks passed for attach and exec at 24x80, 43x137 and 18x62; controls do not leak into stdin.
- Debug/release dashboard builds passed with --warning-mode=fail. Browser checks confirmed fullscreen viewport fallback, Escape with xterm focused, TTY restoration (30x115), and session closure on HTMX navigation with no console errors. Native browser fullscreen was denied during automation; the fallback was exercised. Test container/server were removed/stopped; port 8080 is free.
- Previous validation remains: 195 library tests and full build/Dokka passed for stream reliability, unsigned counters and deprecation cleanup.

## Next Steps
1. Review/merge MR #4 including dashboard sizing; publish 0.1.0 only when requested.
2. Next single-host readiness work: expose image-operation progress, validate persistent-volume/recreate workflows, and establish PR compatibility checks.

## Known Risks / Constraints
- CI workflow changes were statically validated; no remote publish/docs workflow was dispatched during this task.
- API 1.51 is fixed; no version negotiation. Release CI provisions Docker 28.5.2.
- CIO reports disconnect between complete HTTP chunks as EOF even without a terminal zero chunk. Recovery must handle EOF as well as exceptions; finite event history also requires refreshing resource state.
- Unsigned schema counters now have unsigned Kotlin types, a model API change in 0.1.0.
- incoming.first()/take() closes the session; output is collected once. Binary chunks may split UTF-8 and require streaming decoding.
- Authentication/roles/audit belong to the application; sample still binds to loopback.
- The review report is local and excluded via .git/info/exclude; never commit it.
- Do not move v0.0.9/v0.0.10 or re-upload the published Maven version.

## Decisions Pending
- Supported Docker/platform matrix and public API compatibility gates for 1.0.0.

## Resume Commands
- `./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 :sample:htmxDashboard:linkReleaseExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2`
- `git diff --check`
- `./gradlew :lib:jvmTest --warning-mode=fail --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2`
