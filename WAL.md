# WAL (Write-Ahead Log)

## Current Focus
- Preparing one release `1.0.0-rc` on `codex/prepare-1.0.0-rc`, based on published 0.1.0 (e12c96a). The owner requested sequential implementation without intermediate releases.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.rc`.
- 0.1.0 is available on GitHub and Maven Central for all four publications. Never move its tag or re-upload artifacts.

## Completed in Last Session
- Step 1: PR CI with read-only permissions, no release secrets, all-platform build + Dokka, warnings fail, and unconditional XML artifact upload.
- Stabilized logs snapshot by awaiting container completion. Repeated terminal cleanup now has bounded per-session deadlines, a larger overall budget, and an assertion that the final peer connection closes.
- Development version is 1.0.0-rc; README points consumers to published 0.1.0. Roadmap explicitly consolidates the remaining scope into this candidate.
- Focused JVM regressions passed. Full `build :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2` passed in 3m45s (JVM/Node/Linux and samples).

## Next Steps
1. Validate lifecycle/recreate with environment, ports, network, and persistent volumes. Discovery found `Containers.create` accepts only ContainerConfig, which cannot carry HostConfig/NetworkingConfig; add typed support and real-Docker coverage.
2. Expose image progress and terminal outcomes; validate cancellation.
3. Test isolated daemon restart/resubscription; establish Docker/platform and API compatibility gates, migration guide, dashboard acceptance checklist.
4. Run final acceptance before tagging/publishing 1.0.0-rc.

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
