# WAL (Write-Ahead Log)

## Current Focus
- Preparing one release `1.0.0-rc` on `codex/prepare-1.0.0-rc`, based on published 0.1.0 (e12c96a). The owner requested sequential implementation without intermediate releases.
- Draft MR #5: https://github.com/LimeBeck/kmp-docker-client/pull/5. Steps 1–2 are commits 7301be2/a40a266. PR CI 34686728810 exposed a mock-server Broken pipe race; it is now reproduced and fixed. Step 3 image progress is commit fdbe29d; fixture fix 8ca92af passed PR CI 34687877135.
- Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#milestones.rc`.
- 0.1.0 is available on GitHub and Maven Central for all four publications. Never move its tag or re-upload artifacts.

## Completed in Last Session
- Owner requested replacing the raw JSON wrapper with explicit serializable models. ImageProgress now has nullable id/status/stream/progress/progressDetail/aux fields; ImageProgressDetail has nullable ULong current/total. Only aux remains JsonObject.
- Progress decoding uses the typed serializer after checking Docker error records. Unknown fields are ignored with the default JSON config. Missing counts remain null; invalid typed values return a malformed-progress error before invoking the callback. Consumer exceptions and cancellation remain outside decoder catches.
- Updated examples, model construction/serialization round-trip tests, unknown-field checks, exact unsigned counters, and HTTP regressions for invalid counts. Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#images.progress.
- Previous CI 34687877135 passed, confirming the deterministic terminal mock fix. Typed-model validation passed: all 214 tests (JVM 98, Node.js 58, Linux 58) and Dokka with warning-mode=fail in 4m12s.

## Next Steps
1. Verify PR CI after pushing typed image progress models.
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
