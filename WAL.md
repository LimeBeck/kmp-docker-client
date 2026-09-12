# WAL (Write-Ahead Log)

## Current Focus
- Version 0.1.0 in MR #4 (`codex/terminal-session-lifecycle`): terminal/session ownership plus reliable logs/stats/events.
- Published baseline remains 0.0.10; no new release tag or publication has been created.
- Contracts: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#exec.streams` and `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.streams.recovery`.

## Completed in Last Session
- Migrated direct and nested JavaScript CI actions to Node.js 24; validated their upstream action.yml manifests and workflow YAML. CI build/test/Dokka now reject Gradle deprecations. Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#runtime`.
- Limited smallBinary to release executables and explicitly opted the terminal sample into the experimental Gradle plugin API. No blanket warning suppression was added.
- Bounded TTY/JSON records to 1,048,576 characters and multiplex log frames to 1,048,576 bytes before allocation. CR/LF/CRLF and complete final unterminated records are preserved.
- Removed all maintained readUTF8Line calls. Oversized image progress returns ErrorResponse; cancellation propagates.
- logs/stats/events use rendezvous channelFlow for backpressure and correct operation across CIO dispatchers. Removed the fixed log duration timeout and applied explicit stream lifetime settings.
- Validate Content-Length after complete consumption; truncated chunk bodies, malformed stats, cancellation, and consumer failures terminate/release streams. Recollection opens a fresh stream without implicit retries.
- Fixed OpenAPI generation of uint64/uint32 as ULong/UInt. Real stats previously overflowed Int for system_cpu_usage; regression coverage includes ULong.MAX_VALUE and values beyond JavaScript Number precision.
- Added common parser/counter tests, real-Docker stats/events coverage, and Unix-socket failure/recovery cases. All 195 tests passed (89 JVM, 53 Node.js, 53 Linux). Full build, debug/release samples, and Dokka passed with --warning-mode=fail and no compiler/deprecation warnings.
- README documents limits, model API changes, event overlap/deduplication/snapshot recovery, and EOF/error resubscription.
- Earlier terminal work remains: immediate incomingChunks, scoped prefix forwarding, idempotent session close, native descriptor cleanup, and dashboard WebSocket migration.

## Next Steps
1. Review/merge MR #4; publish 0.1.0 only when requested.
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
- `git diff --check`
- `./gradlew :lib:jvmTest --warning-mode=fail --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2`
