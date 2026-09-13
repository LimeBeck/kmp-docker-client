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
1. Owner explicitly approved the exact push on 2026-09-13. Commits 3754f77 and 59aa2bb were pushed to LimeBeck/kmp-docker-client branch codex/prepare-1.0.0-rc; MR #5 description now reflects completed acceptance work.
2. CI run 34745337397 passed three matrix cells and isolated restart (16s), but dashboard acceptance stayed in progress for over 10 minutes. Added an 8-minute step limit to each acceptance suite in PR/release workflows so always() uploads can run before the job budget is exhausted. The cancelled run logs confirmed every dashboard assertion passed by 07:34:25 UTC; the remaining orphan native process showed teardown was stuck waiting after SIGTERM. Bounded runner teardown now waits 10s then kills only its own child. Full local acceptance passed with bounded teardown and no remaining fixture containers; the SIGTERM-ignoring process probe passed in 10.2s. Re-run CI; do not mark ready until green.
3. After all checks pass, make MR #5 ready for review. Candidate tag/publication and fresh Maven resolution are release actions, not implied by preparing the candidate.
4. Do not move existing tags or republish 0.1.0. No RC was published in this preparation step.

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
- Owner selected one future release with BOTH macOS and Windows on BOTH JVM and Native. Recorded in spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#deferred.desktop-platforms (local roadmap edit, not part of the two approved pushed commits). Version/date and exact architecture matrix remain to be selected; current RC scope is unchanged.
- Merge/release timing after final CI; fresh Maven resolution occurs after candidate publication.

## Resume Commands
- `./gradlew :sample:htmxDashboard:linkDebugExecutableLinuxX64 :sample:htmxDashboard:linkReleaseExecutableLinuxX64 --warning-mode=fail --console=plain --max-workers=2`
- `git diff --check`
- `./gradlew :lib:jvmTest --warning-mode=fail --console=plain --max-workers=2`
- `./gradlew build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2`
