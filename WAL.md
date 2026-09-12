# WAL (Write-Ahead Log)

## Current Focus
- Preparing one release `1.0.0-rc` on `codex/prepare-1.0.0-rc`, based on published 0.1.0 (e12c96a). The owner requested sequential implementation without intermediate releases.
- Draft MR #5: https://github.com/LimeBeck/kmp-docker-client/pull/5. Steps 1–2 are commits 7301be2/a40a266. PR CI 34686728810 exposed a mock-server Broken pipe race; it is now reproduced and fixed. Step 3 image progress is commit fdbe29d; fixture fix 8ca92af passed PR CI 34687877135.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.rc`.
- 0.1.0 is available on GitHub and Maven Central for all four publications. Never move its tag or re-upload artifacts.

## Completed in Last Session
- Diagnosed PR CI run 34700163603: JDK 17 could not load common:1.0.3 logger classes compiled for Java 21; Docker 29 containerd returned GraphDriver.Data=null rejected by generated models.
- Removed the library common logger dependency in lib/build.gradle.kts and switched DockerClient/ExecSession/HijackHandshake to Ktor logging. The native dashboard retains its own explicit dependency. JDK 17/21 matrix is unchanged.
- Explicitly corrected DriverData.Data nullability in specs/v1.51.yaml and added common regression tests for both null containerd metadata and preserved overlay2 maps in container/image inspection. Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#models.storage.
- Regenerated JVM and JS/Linux ABI snapshots; reviewed diff contains only the logger type and DriverData nullability changes. Documented migration from 0.1.0. No checks were disabled.
- Actual Temurin 17 local allTests/updateKotlinAbi passed in 6m52s: JVM 102, Node.js 61, Linux X64 61 tests, no failures/skips. Separate checkKotlinAbi passed in 7s, all with --warning-mode=fail. Focused HTTP logging regressions also passed before the full run.
- Previous RC work includes complete create configuration, typed generic image progress, terminal cleanup checks, the four-cell Docker/JDK matrix, warning-as-error compilation, ABI guards and acceptance/migration documentation.

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
