# WAL (Write-Ahead Log)

## Current Focus
- One MR on codex/prepare-1.0.1 contains all requested AutoCloseable, KDoc and terminal scrollbar changes. Owner selected 1.0.1; no new release is authorized by the MR request.
- Contracts: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#client.ownership, spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#exec.dashboard-sizing and spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#docs.

## Completed in Last Session
- DockerClient implements AutoCloseable; close delegates to owned HttpClient. Examples use .use; migration notes distinguish upcoming 1.0.1 from published 1.0.0. Raw sessions retain independent ownership.
- Lifecycle JVM test and test/example compilation on JVM/JS/Linux X64 passed. ABI adds only AutoCloseable and close(); check passed. KDoc covers 63 domain methods plus client/config/auth/Result/ExecSession; Dokka and six key generated pages/links verified.
- Dashboard scrollbar hides only in alternate buffer (mc/vim), returns in shell, and refits on switches. Listener is disposed. Dashboard Kotlin compilation passed.
- v1.0.0 is immutable at 16ea5077. Release CI 34765187140, Docs CI 34765167302 and PR CI 34754961852 passed. Maven common/JVM/JS/Linux X64 1.0.0 and dependencies now resolved in /tmp/kmp-stable-consumer (89s); publication follow-up docs updated.

## Next Steps
- Push the three preparation commits, create the common MR and inspect PR CI before merging. Final local Dokka/ABI/lifecycle checks passed. Log: /tmp/kmp-1.0.1-final-check.log.
- After merge, owner may authorize v1.0.1 publication. Do not move existing tags or republish Maven versions.

## Known Risks / Constraints
- API 1.51 fixed, no negotiation. Supported Linux JVM/Node/Linux X64 only.
- Dashboard UI acceptance remains outside SDK release gates; retain Kotlin SDK and isolated-daemon checks.
- Local review report stays ignored and must never be committed.
- Running dashboard has not been restarted; relink/restart to see UI changes.

## Decisions Pending
- Future macOS/Windows release must include both JVM and Native. Version/date remain unassigned.

## Resume Commands
- `git diff --check`
- `./gradlew :lib:dokkaGenerateHtml :lib:checkKotlinAbi :lib:jvmTest --tests '*DockerClientLifecycleTest' --warning-mode=fail --console=plain --max-workers=2`
