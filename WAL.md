# WAL (Write-Ahead Log)

## Current Focus
- PR #3 is open for review: https://github.com/LimeBeck/kmp-docker-client/pull/3.

## Completed in Last Session
- Fixed container table overflow: bounded columns, wrapping image digests/names, scrollable keyboard-focusable region, and responsive viewport/navigation. Spec: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-000.md#samples.dashboard`.
- Debug and release dashboard builds passed. Browser validation at desktop and 390px widths confirmed visible columns, no page-wide overflow, keyboard scrolling, and correct styles after HTMX navigation. Local dashboard restarted with the fix.
- Kept the review report local as requested: excluded it from PR commits and added a local Git exclusion. Source code and validated build output are unchanged.
- Opened PR #3 into master; branch published through existing GitHub SSH authentication. No merge or release was performed.
- Packaged the verified changes into focused commits on `codex/modernize-and-fix-review`; target is `LimeBeck/kmp-docker-client:master`.
- R1/R2: masked HTTP auth headers, excluded bodies/auth exchanges and raw hijack headers from logs; bound dashboard to loopback.
- R3/R4: shared `/v1.51/` path builder for HTTP/hijack; pull/push/load validate streamed progress and embedded errors.
- R5/R6/R7: cold streams check HTTP status and throw DockerApiException; event consumer exceptions propagate; HEAD/empty/malformed HTTP error bodies fall back to status-based ErrorResponse.
- R8: build/test always upload distinct JUnit artifacts; report publication waits for both jobs, including skipped test jobs.
- Full `./gradlew build :lib:dokkaGenerateHtml --console=plain --max-workers=2` passed in 6m 30s, including both sample applications and documentation.
- Dashboard runtime smoke check passed: only 127.0.0.1:8080 listens, GET / redirects to /system; process stopped afterward.
- Added 20 JVM HTTP regression tests using a Unix socket mock. All 20 passed. An existing exec integration test hit a container-name collision; removed its two small random-name pools so Docker assigns unique names. Final test reports: JVM 52, JS 32, Linux 32, all passing (116 executions).
- Updated contracts explicitly: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-000.md#runtime`, `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-000.md#serialization`, `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-000.md#auth.logging`, `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-000.md#samples.dashboard`; `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#images.progress`, `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.streams`; `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#ci.jobs`.
- Touched: DockerClient, all API wrappers, HijackHandshake, new DockerApiException and JVM mock/tests, common ExecTest, dashboard main, release workflow, README, PROP-000/001/002, WAL.
- Prior modernization remains in the working tree: Kotlin 2.4.20, Gradle 9.7.1, Ktor 3.5.2, updated libraries/plugins, Yarn lockfile, build/POM fixes, README and CI/cache changes.

## Next Steps
1. Review the PR from `codex/modernize-and-fix-review` into `master`; no merge or release is requested.
2. Prioritize broader transport lifecycle coverage and compatibility/release guarantees before API expansion.
3. Expand domains following `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-001.md#phases` after stabilization.

## Known Risks / Constraints
- Cold Flow preparation success is not HTTP request success; collection errors use DockerApiException. README and specs document this contract.
- TTY newline buffering was not selected for this repair batch and remains open.
- GitHub workflow changes are validated locally; remote CI/publication, signing secrets, Maven Central, and Pages settings are not exercised.
- Existing integration tests use the local Docker daemon. No pre-existing conflicting container was removed.
- Temporary review probes in `/tmp/ktor-review` describe old failures and are not part of the regression suite.

## Decisions Pending
- Accept remaining roadmap scope and ordering.
- Define byte/chunk vs line streaming API and connection ownership beyond the preserved cold Flow contract.
- Define supported Docker daemon matrix and release compatibility guarantees.

## Resume Commands
- `git diff --check`
- `./gradlew :lib:jvmTest --tests '*DockerHttpRegressionTest' --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --console=plain --max-workers=2`
