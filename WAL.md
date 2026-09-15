# WAL (Write-Ahead Log)

## Current Focus
- Exception context: safe suppressed metadata for HTTP/request/response/stream and exec/attach boundaries, preserving original exception types/causes and cancellation. Non-HTTP handshake failures now throw instead of losing the cause in an error string. Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.context. SDK error Results also retain context through map/mapError/getOrThrow; DockerResultException retains raw error and available cause with a safe message. Caller-created Results/raw channels outside SDK boundaries may have no context.
- Privacy audit: removed raw endpoint/cause from unreleased diagnostic reports and excluded probes from SDK HTTP logging. Synthetic secret/report/DEBUG-log checks passed, including sensitive socket paths, exception cause/suppressed messages and arbitrary response headers/body. Six diagnostic tests, JVM/JS/Linux X64 compilation, Dokka and updated ABI checks passed. No endpoint or Throwable remains in reports; original exceptions/cancellation and custom client logging remain outside this guarantee. Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.diagnostics.
- Connection diagnostics included in MR #8 on codex/prepare-1.0.1 at the owner’s request: opt-in versioned ping probe and failure classification, bounded response and cancellation preservation. Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.diagnostics. Target release: 1.0.1.
- One MR on codex/prepare-1.0.1 contains diagnostics, safe exception context, AutoCloseable, KDoc and terminal scrollbar changes. Owner selected 1.0.1; no new release is authorized by the MR request.
- Contracts: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#client.ownership, spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#exec.dashboard-sizing and spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#docs.

## Completed in Last Session
- KDoc usability improvements for MR #8: documented log option units/defaults, container creation/name/port semantics, filter examples and contextual Result/exec failures. Linked key APIs to existing compiled guide samples through Dokka samples configuration. Dokka and guide compilation passed with --warning-mode=fail; rendered samples/local links verified on getOrThrow, getLogs, startInteractive and diagnoseConnection. No runtime changes. Logs: /tmp/kdoc-improvement.log and /tmp/kdoc-final.log. Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#docs.
- Exception operation context: full SDK suite passed (118 JVM + 62 JS + 62 Linux X64 = 242, no failures/skips). Dokka and compiled guide passed; dockerContext API page verified. Context/read-write/Result/getOrThrow/map/mapError/cancellation/privacy regression tests passed. Logs: /tmp/kmp-operation-all-checks.log; final ABI check: /tmp/kmp-operation-abi-check.log.
- DockerClient implements AutoCloseable; close delegates to owned HttpClient. Examples use .use; migration notes distinguish upcoming 1.0.1 from published 1.0.0. Raw sessions retain independent ownership.
- Lifecycle JVM test and test/example compilation on JVM/JS/Linux X64 passed. ABI adds only AutoCloseable and close(); check passed. KDoc covers 63 domain methods plus client/config/auth/Result/ExecSession; Dokka and six key generated pages/links verified.
- Dashboard scrollbar hides only in alternate buffer (mc/vim), returns in shell, and refits on switches. Listener is disposed. Dashboard Kotlin compilation passed.
- v1.0.0 is immutable at 16ea5077. Release CI 34765187140, Docs CI 34765167302 and PR CI 34754961852 passed. Maven common/JVM/JS/Linux X64 1.0.0 and dependencies now resolved in /tmp/kmp-stable-consumer (89s); publication follow-up docs updated.

## Next Steps
- Integrate diagnostics with the existing api()/ApiDelegate extension mechanism per spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#deferred.api-extensions; cover opt-in safe diagnostic metadata, preserved lifecycle/error behavior, compatibility and updating the existing extension example; do not redesign extension support. Version/date unassigned.
- TODO in diagnostics/OperationContext.kt: replace the closed route allowlist with explicit safe route metadata supplied by API extensions; preserve /{unknown} fallback. The API is intentionally extensible. No behavior change in this reminder.
- Review combined MR #8: https://github.com/LimeBeck/kmp-docker-client/pull/8 and inspect CI for the updated head before merging. Local SDK suite (242 tests), Dokka and ABI checks passed.
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
