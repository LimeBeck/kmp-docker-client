# WAL (Write-Ahead Log)

## Current Focus
- Preparing the owner-requested MR on codex/remove-dashboard-acceptance, based on master 3906958a. Scope: remove dashboard acceptance infrastructure and keep SDK integration/recovery gates.
- Owner authorized publication of 1.0.0-rc on 2026-09-13. MR #5 merged as 3906958a42a76624c28ab398b223b1c3cbf730ad; its tree matches the four-cell CI-tested c278d8f.
- Tag v1.0.0-rc is pushed and immutable. Release CI: https://github.com/LimeBeck/kmp-docker-client/actions/runs/34747664859. GitHub prerelease is public: https://github.com/LimeBeck/kmp-docker-client/releases/tag/v1.0.0-rc. Release CI fully passed; Maven deployment 65781810-2e71-4fb1-ba85-855d77ca0499 is published; independent common/JVM/JS/Linux X64 dependency resolution passed on 2026-09-13.
- Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#publish.

## Completed in Last Session
- Owner removed dashboard acceptance from SDK release requirements. Deleted scripts/dashboard-acceptance.py and scripts/run-dashboard-acceptance.sh, their PR/release CI steps and dashboard log artifact paths. SDK tests, isolated daemonRestartTest, its harness and JUnit uploads remain.
- Renamed docs/DASHBOARD-ACCEPTANCE.md to docs/DASHBOARD.md and kept sample usage guidance with optional manual smoke checks. Updated README, RC checklist and normative specs per explicit owner correction: spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance and spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#ci.acceptance.
- Verified both workflow YAMLs parse, retain the SDK recovery task and always-uploaded test reports, and have no references to removed dashboard scripts. git diff --check passes. SDK/sample source code is unchanged.

## Next Steps
1. Create the requested MR from codex/remove-dashboard-acceptance and check its CI. Workflow YAML parsing, retained SDK recovery/JUnit gates, documentation references and diff whitespace checks pass. Library and sample source code were not changed.
2. Release 1.0.0-rc is published and all four Maven artifacts resolve successfully. Do not move its tag or republish it. Continue the SDK roadmap separately.

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
