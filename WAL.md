# WAL (Write-Ahead Log)

## Current Focus
- Preparing stable 1.0.0 and a Dokka-integrated usage guide on codex/prepare-1.0.0-docs, based on merged master 48b3dc2 (MR #6).
- Scope: version/docs/build verification only. No new SDK API, dashboard acceptance or platform implementation. Stable release publication is not authorized by preparation.
- Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.stable and spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#docs.

## Completed in Last Session
- Set libVersion=1.0.0; published installation baseline remains 1.0.0-rc until stable artifacts exist.
- Added docs/USAGE.md with connection, ownership, errors, lifecycle, data retention, streaming, terminals, authentication, image progress and recovery. Marked Kotlin blocks are extracted into commonTest and compiled; Dokka depends on JVM compilation, not test execution.
- Updated README, compatibility and migration guidance, retained the old RC migration URL as a redirecting document, and added docs/RELEASE-1.0.0.md for final evidence.
- Full local build passed: 224 SDK tests (102 JVM, 61 JS, 61 Linux X64), no failures/skips, unchanged ABI and compiled guide examples on every target. Final Dokka generation passed; verified guide sections/code and 14 local links. Isolated SDK restart test passed (11s Gradle run); fixture cleaned up.

## Next Steps
1. Local preparation checks are complete. Create the preparation MR and verify final remote CI. Logs: /tmp/kmp-stable-build.log, /tmp/kmp-stable-dokka-final.log, /tmp/kmp-stable-recovery.log.
2. Create preparation MR and verify the full Docker/JDK matrix. Record final commit/run evidence in its body. Stable tag and Maven/GitHub publication remain separate owner-authorized actions.
3. Published v1.0.0-rc is immutable; all four Maven artifacts and dependencies already resolved successfully. Do not republish it.

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
- Owner selected one future release with BOTH macOS and Windows on BOTH JVM and Native. Recorded in spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#deferred.desktop-platforms (separate roadmap commit). Version/date and exact architecture matrix remain to be selected; current RC scope is unchanged.
- Scheduling the next SDK release; current RC publication and Maven resolution are complete.

## Resume Commands
- `./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 :sample:htmxDashboard:linkReleaseExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2`
- `git diff --check`
- `./gradlew :lib:jvmTest --warning-mode=fail --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2`
