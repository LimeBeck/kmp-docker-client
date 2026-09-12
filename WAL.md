# WAL (Write-Ahead Log)

## Current Focus
- Preparing one release `1.0.0-rc` on `codex/prepare-1.0.0-rc`, based on published 0.1.0 (e12c96a). The owner requested sequential implementation without intermediate releases.
- MR #5: https://github.com/LimeBeck/kmp-docker-client/pull/5. Steps 1–2 are commits 7301be2/a40a266. PR CI 34686728810 exposed a mock-server Broken pipe race; it is now reproduced and fixed. Step 3 image progress is commit fdbe29d; fixture fix 8ca92af passed PR CI 34687877135.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.rc`.
- 0.1.0 is available on GitHub and Maven Central for all four publications. Never move its tag or re-upload artifacts.

## Completed in Last Session
- Owner requested the remaining RC preparation on 2026-09-12. Isolated daemon restart acceptance is commit 3754f77; never restart the user's main daemon. The prior four-cell matrix passed on 7d2d9dd (run 34714529905).
- Extended the htmx example with explicit create configuration, staged replacement/rollback/confirmation for sample-managed containers, memory stats, bounded log/event views and typed pull progress/cancellation. Replacement retains named volumes and requires explicit readiness confirmation; shared-volume writes are not automatically rolled back.
- Browser/application acceptance exposed and fixed empty exec commands, DELETE redirects and native fullscreen Escape. Added per-view connection ownership and serialized write heartbeats to release idle subscriptions when HTTP clients disconnect.
- Added scripts/dashboard-acceptance.py and scripts/run-dashboard-acceptance.sh, invoked by PR/release CI through the disposable-daemon harness. Full local HTTP/WebSocket acceptance passed, including independent HTTP reachability/data checks, rollback/failure paths and socket cleanup (1 → 1 after repeated active/idle sessions and cancelled pulls).
- Browser checks passed form creation, default shell, pull success/error/cancel, fullscreen/Escape and resizing (28×115 → 45×136 → 21×76). The 375px viewport had no document-level horizontal overflow.
- docs/DASHBOARD-ACCEPTANCE.md and docs/RC-ACCEPTANCE.md record evidence and boundaries. Final API/migration review against 0.1.0 found only the already documented logger/nullable metadata changes plus additive create/progress overloads. Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance.

## Next Steps
1. Final local application harness exited successfully with complete cleanup; native debug build and checkKotlinAbi passed. Push and verify the remote four-cell CI with both acceptance harnesses; exact head/run evidence belongs in MR #5.
2. After all checks pass, make MR #5 ready for review. Candidate tag/publication and fresh Maven resolution are release actions, not implied by preparing the candidate.
3. Do not move existing tags or republish 0.1.0. No RC was published in this preparation step.

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
- Merge/release timing after final CI; fresh Maven resolution occurs after candidate publication.

## Resume Commands
- `./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 :sample:htmxDashboard:linkReleaseExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2`
- `git diff --check`
- `./gradlew :lib:jvmTest --warning-mode=fail --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2`
