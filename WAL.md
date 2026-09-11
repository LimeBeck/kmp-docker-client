# WAL (Write-Ahead Log)

## Current Focus
- Version 0.0.10 is published and publicly available in Maven Central.
- Next target: reliable support for a single-host Docker management panel, per `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#goal`.

## Completed in Last Session
- Merged PR #3, including dependency modernization, eight review fixes, regression coverage, and responsive dashboard layout.
- v0.0.9 CI failed because the runner daemon supported API 1.48 while the SDK requires 1.51. Maven publication was skipped; the tag is preserved.
- Fixed CI by provisioning Docker 28.5.2, exposing its Unix socket at the SDK path, and checking /v1.51/_ping before build/test. Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#runtime`.
- Released v0.0.10 at 73b8968. All release jobs passed: https://github.com/LimeBeck/kmp-docker-client/actions/runs/34643318776.
- Central deployment a1caeaa6-cceb-4074-a0eb-23f7b372d320 validated and published automatically. Confirmed HTTP 200 and version 0.0.10 for POMs, Gradle metadata, and referenced files of docker-client, docker-client-jvm, docker-client-js, and docker-client-linuxx64.
- Earlier local full build/Dokka and 116 test executions passed; dashboard debug/release builds and desktop/mobile/HTMX checks passed. The local dashboard was restarted with the layout fix.
- Accepted the single-host 1.0.0 goal in FEAT-002; deferred Swarm/domain expansion in FEAT-001.
- Recorded the user's requested deprecation cleanup before 1.0.0, starting with readUTF8Line. Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#maintenance.deprecations`.

## Next Steps
1. Define and implement terminal/stream lifecycle contracts and regression cases under FEAT-002.
2. Validate single-host container workflows, persistence, operation progress, and application-level Compose integration.
3. Resolve Kotlin, Gradle/plugin, and CI deprecation warnings at their source; do not hide them with blanket suppression.

## Known Risks / Constraints
- API 1.51 is fixed; version negotiation is not implemented. README documents the daemon requirement.
- Cold Flow preparation success is not HTTP success; collection errors use DockerApiException.
- TTY newline buffering remains open. Authentication/roles/audit belong to the dashboard application; the sample binds only to loopback.
- The review report is intentionally local and excluded via .git/info/exclude; never add it to commits.
- Do not move v0.0.9/v0.0.10 or re-upload the published Maven version.

## Decisions Pending
- Detailed byte/chunk terminal API, connection ownership, and the supported daemon/platform matrix for 1.0.0.

## Resume Commands
- `git diff --check`
- `./gradlew :lib:jvmTest --tests '*DockerHttpRegressionTest' --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --console=plain --max-workers=2`
