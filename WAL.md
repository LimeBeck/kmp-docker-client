# WAL (Write-Ahead Log)

## Current Focus
- Preparing one release `1.0.0-rc` on `codex/prepare-1.0.0-rc`, based on published 0.1.0 (e12c96a). The owner requested sequential implementation without intermediate releases.
- Draft MR #5: https://github.com/LimeBeck/kmp-docker-client/pull/5. Steps 1–2 are commits 7301be2/a40a266. PR CI 34686728810 exposed a mock-server Broken pipe race; it is now reproduced and fixed. Step 3 image progress is commit fdbe29d; fixture fix 8ca92af passed PR CI 34687877135.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.rc`.
- 0.1.0 is available on GitHub and Maven Central for all four publications. Never move its tag or re-upload artifacts.

## Completed in Last Session
- Owner requested operation-specific generic aux. ImageProgress<out TAux> now uses nullable TAux with a serializer chosen by the endpoint. Push callbacks receive ImageProgress<ImagePushResult> (Tag/Digest/Size wire fields); create/load receive ImageProgress<Unit> because Docker API 1.51 has no documented aux payload for those operations. No public progress field uses JsonObject.
- Verified push payload against Moby v28.5.2 api/types/types.go. Corrected the earlier load-ID example: load outputs status/stream, not a guaranteed aux ID. Updated docs/IMAGE-PROGRESS.md and spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#images.progress explicitly.
- Tests cover endpoint-specific typed access, generic serializer round trips, optional aux, invalid push Size, exact unsigned counts, errors/cancellation and real pull/load. All 218 tests (JVM 100, Node.js 59, Linux 59) and Dokka passed with warning-mode=fail in 3m39s.
- Separate fixture commit 54140d9 counts both EOF and Broken pipe/reset as closed connections, requiring all 20 closes. The old assertion incorrectly required 10 kernel errors; synchronized early-close ordering remains enforced.

## Next Steps
1. Verify PR CI after pushing generic operation-specific progress and fixture close accounting.
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
