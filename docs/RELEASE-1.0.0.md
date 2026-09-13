# Stable 1.0.0 preparation

Scope: the same supported SDK/API as published 1.0.0-rc, with stable version metadata and a compiled usage guide integrated into Dokka. Dashboard acceptance and new platform transports are not release gates.

## Before tagging

- [x] Set the development version to 1.0.0; do not claim that Maven 1.0.0 is already published.
- [x] Document the API compatibility policy and migration from 0.1.0/0.0.x; published RC callers need no source migration.
- [x] Provide installation, connection, lifecycle, progress, streaming, terminal, errors and recovery examples in the Dokka module guide.
- [x] Compile guide examples on JVM/JS/Linux X64, generate Dokka without warnings, and verify guide/API links in the generated HTML.
- [ ] Validate unchanged ABI and the complete Docker/JDK PR matrix on the final proposed stable commit; keep isolated SDK restart testing.
- [ ] Merge the preparation MR and verify the intended release commit/tree.

## Publication (requires release authorization)

- [ ] Create v1.0.0 at the validated commit. Never move existing tags or republish Maven versions.
- [ ] Complete Release CI and publish a non-prerelease GitHub release.
- [ ] Resolve common/JVM/JS/Linux X64 version 1.0.0 and dependencies from Maven Central in an independent consumer.
- [ ] Change the installation example from 1.0.0-rc to the available stable 1.0.0 and update publication status.

## Evidence

- Published candidate: v1.0.0-rc at 3906958a; release CI 34747664859 passed and all four Maven variants resolved.
- Dashboard acceptance removal: MR #6 merged as 48b3dc2. Docs CI passed; no PR matrix was reported for that removal. Stable preparation must run its own full matrix.
- Local full build passed with 224 tests (102 JVM, 61 JS, 61 Linux X64), zero failures/skips; guide examples compiled on all targets and ABI remained unchanged. Final Dokka generation passed with warnings-as-errors; guide sections, code blocks and 14 local links were verified. Isolated SDK daemonRestartTest passed and the disposable daemon was removed.
- Stable version is not published by this preparation.

Contracts: spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance and spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#docs.
