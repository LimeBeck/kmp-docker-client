# WAL (Write-Ahead Log)

## Current Focus
- Preparing one release `1.0.0-rc` on `codex/prepare-1.0.0-rc`, based on published 0.1.0 (e12c96a). The owner requested sequential implementation without intermediate releases.
- Draft MR #5: https://github.com/LimeBeck/kmp-docker-client/pull/5. Steps 1–2 are commits 7301be2/a40a266. PR CI 34686728810 exposed a mock-server Broken pipe race; it is now reproduced and fixed. Step 3 image progress is commit fdbe29d.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.rc`.
- 0.1.0 is available on GitHub and Maven Central for all four publications. Never move its tag or re-upload artifacts.

## Completed in Last Session
- Steps 1–2 already added PR CI, stabilized log snapshots, complete container creation, persistent-volume recreation tests and documentation.
- Step 3: added suspending onProgress overloads to image create/push/load, retaining old signatures. ImageProgress preserves raw records and optional typed fields with exact unsigned counts. Callbacks are ordered/backpressured; final Result reports daemon completion/errors, cancellation and consumer failures propagate unchanged.
- Image operations now detect short Content-Length responses and have caller-owned deadlines. Added docs/IMAGE-PROGRESS.md and contract spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#images.progress.
- All 211 tests passed (JVM 97, Node.js 57, Linux 57), including real pull/load and mock push/progress failures/cancellation. Dokka passed with warning-mode=fail.
- CI terminal failure fixed: the mock now accepts peer aborts only for explicitly marked early-close replies. Latches force 10 uncollected sessions to close before prompt delivery, and the test asserts all 20 requests finish and all 10 early aborts occur. No sleeps or expanded deadlines in the fix. All HTTP regressions passed after the change; mock failures are surfaced immediately when a test fails.

## Next Steps
1. Verify PR CI after pushing image progress and deterministic mock cleanup fixes.
2. Test isolated daemon restart/resubscription; establish Docker/platform and API compatibility gates, migration guide, dashboard acceptance checklist. Do not restart the user’s real Docker daemon.
3. Run final acceptance before tagging/publishing 1.0.0-rc.

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
- Supported Docker/platform matrix and public API compatibility gates for 1.0.0.

## Resume Commands
- `./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 :sample:htmxDashboard:linkReleaseExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2`
- `git diff --check`
- `./gradlew :lib:jvmTest --warning-mode=fail --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2`
