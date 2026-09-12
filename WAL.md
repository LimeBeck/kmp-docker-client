# WAL (Write-Ahead Log)

## Current Focus
- Preparing one release `1.0.0-rc` on `codex/prepare-1.0.0-rc`, based on published 0.1.0 (e12c96a). The owner requested sequential implementation without intermediate releases.
- Draft MR #5: https://github.com/LimeBeck/kmp-docker-client/pull/5. Steps 1–2 are commits 7301be2/a40a266. PR CI 34686728810 exposed a mock-server Broken pipe race; it is now reproduced and fixed. Step 3 image progress is commit fdbe29d; fixture fix 8ca92af passed PR CI 34687877135.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.rc`.
- 0.1.0 is available on GitHub and Maven Central for all four publications. Never move its tag or re-upload artifacts.

## Completed in Last Session
- Owner requested the remaining RC preparation on 2026-09-12, resuming the previously deferred isolated restart work. Never restart the user's main daemon.
- Previous four-cell compatibility CI passed on 7d2d9dd: https://github.com/LimeBeck/kmp-docker-client/actions/runs/34714529905.
- Added scripts/with-isolated-docker.sh and the explicit :lib:daemonRestartTest task. The harness owns a labelled DinD container, separate socket/data volume, and cleanup. Ordinary JVM tests exclude this opt-in destructive-to-fixture test.
- Local restart acceptance passed: logs/stats/events/terminal all reached EOF before cleanup; the same client reconnected, reconciled state, resumed streams and opened repeated terminals. Socket count 2 → 3. Added recovery policy and PR/release CI steps. Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance.
- API/migration comparison against published 0.1.0 confirms additive create/progress overloads plus documented logger type and DriverData nullability changes; no new library API changes in this step.

## Next Steps
1. Complete real dashboard acceptance; sample needs lifecycle/progress integration before full application acceptance can be claimed. An optional question about using the sample was sent; default is the bundled htmx dashboard.
2. Run the complete final CI matrix including the new isolated recovery step, then record exact head/run evidence in MR #5.
3. Keep the MR unmerged and the candidate unpublished until preparation/acceptance is complete. Release/tag/publication follows the owner's release instruction.

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
- Real-dashboard acceptance and final release readiness.

## Resume Commands
- `./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 :sample:htmxDashboard:linkReleaseExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2`
- `git diff --check`
- `./gradlew :lib:jvmTest --warning-mode=fail --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2`
