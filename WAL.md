# WAL (Write-Ahead Log)

## Current Focus
- Preparing one release `1.0.0-rc` on `codex/prepare-1.0.0-rc`, based on published 0.1.0 (e12c96a). The owner requested sequential implementation without intermediate releases.
- Draft MR #5: https://github.com/LimeBeck/kmp-docker-client/pull/5. Steps 1–2 are commits 7301be2/a40a266. PR CI 34686728810 exposed a mock-server Broken pipe race; it is now reproduced and fixed. Step 3 image progress is commit fdbe29d; fixture fix 8ca92af passed PR CI 34687877135.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.rc`.
- 0.1.0 is available on GitHub and Maven Central for all four publications. Never move its tag or re-upload artifacts.

## Completed in Last Session
- Owner deferred daemon restart/resubscription and requested compatibility/readiness work first. FEAT-002 records the order change without marking recovery passed.
- PR CI now has Docker 28.5.2/29.0.0 × JDK 17/21 on Ubuntu 24.04; every cell tests JVM/Node/Linux X64 with distinct report artifacts and fail-fast disabled. Node.js is pinned to 24.16.0 using NodeJsEnvSpec.
- Built-in Kotlin ABI validation enabled for JVM and JS/Linux KLib, including all generated models. References: lib/api/lib.api and lib/api/lib.klib.api. Unsupported targets fail rather than being inferred. CI checks, never updates, snapshots.
- Kotlin warnings now fail compilation across all modules; Gradle warning-mode=fail and Dokka failOnWarning remain enabled. Generator allOf diagnostics are tracked separately.
- Added docs/COMPATIBILITY.md, docs/MIGRATION-1.0.0-rc.md and docs/RC-ACCEPTANCE.md, linked from README. Contracts: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#ci.abi and spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#ci.pr.
- Full local build/checkKotlinAbi/Dokka passed in 8m58s, including 218 tests and sample executables. Negative ABI probe failed as expected for a simulated removed getter; restored baseline passed in 2s. Workflow matrix/permissions/artifact checks passed.

## Next Steps
1. Check the new four-cell PR CI matrix after push; record run URL/SHA in release evidence.
2. Real-dashboard application acceptance and final API/migration review remain manual release gates.
3. Daemon restart/resubscription is explicitly deferred by the owner. Do not execute it now or mark the gate complete. Do not restart the user’s real Docker daemon.
4. Resolve outstanding acceptance gates or obtain an explicit scope revision before tagging/publishing 1.0.0-rc. No release was performed.

## Known Risks / Constraints
- Release CI and Docs CI passed on the merged commit. The first release attempt failed two timing-sensitive tests; the second passed both build and separate allTests jobs.
- API 1.51 is fixed; no version negotiation. Release CI provisions Docker 28.5.2.
- CIO reports disconnect between complete HTTP chunks as EOF even without a terminal zero chunk. Recovery must handle EOF as well as exceptions; finite event history also requires refreshing resource state.
- Unsigned schema counters now have unsigned Kotlin types, a model API change in 0.1.0.
- incoming.first()/take() closes the session; output is collected once. Binary chunks may split UTF-8 and require streaming decoding.
- Authentication/roles/audit belong to the application; sample still binds to loopback.
- The review report is local and excluded via .git/info/exclude; never commit it.
- Do not move v0.0.9/v0.0.10 or re-upload the published Maven version.

## Decisions Pending
- Timing of deferred daemon-restart verification and real-dashboard acceptance before release.

## Resume Commands
- `./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 :sample:htmxDashboard:linkReleaseExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2`
- `git diff --check`
- `./gradlew :lib:jvmTest --warning-mode=fail --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2`
