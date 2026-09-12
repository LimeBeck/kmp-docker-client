# 1.0.0-rc acceptance record

This checklist records evidence required for release. An unchecked item is outstanding, not implicitly passed. No candidate tag or publication is authorized by completing documentation alone.

## Automated checks

- [ ] All four Docker/JDK PR matrix cells pass on the exact commit to be released; each includes JVM, Node.js and Linux X64 tests, samples and Dokka. Record run URL and SHA below.
- [ ] `checkKotlinAbi` passes against committed snapshots for all targets; public API changes and migration notes have been reviewed.
- [ ] Kotlin and Gradle warning-as-error checks pass. Generator diagnostics are tracked separately in COMPATIBILITY.md.
- [x] Real-Docker tests cover create/configuration inspection, name/image errors, restart-policy update and data retained after original/replacement deletion (`ContainerRecreateTest`).
- [x] Image tests cover typed progress, final error/success, malformed records, exact counters, cancellation and consumer exceptions; real pull/load run on all three targets.
- [x] Mock transport tests cover bounded logs/stats/events, HTTP errors, truncation, cancellation and repeated terminal connection cleanup.
- [ ] Isolated daemon restart and application resubscription. **Deferred by the owner on 2026-09-12; do not execute during the current work or mark passed.** This remains an acceptance gap until explicitly completed or the release scope is revised.

## Real dashboard acceptance

Use disposable resources with unique names and explicit cleanup. Record dashboard commit, library commit, Docker version, OS/runtime, date and results; the SDK integration tests do not replace this application check.

- [ ] Create/start a container with environment variables, a loopback-published port, a named volume and a user-defined network; confirm application reachability and configuration.
- [ ] Open exec/attach; verify prompt latency, UTF-8, stdout/stderr behavior, resize, fullscreen/Escape and cleanup on navigation.
- [ ] Observe logs/stats/events, cancel each subscription, and verify no accumulating connections during repeated navigation.
- [ ] Recreate with changed environment/image while retaining data. Check readiness, then remove the old instance. Exercise a failed replacement and the application's rollback path.
- [ ] Delete the replacement while retaining data; confirm a new reader can access it. Only then explicitly remove the disposable named volume.
- [ ] Display image progress and final error; cancel a long operation and reconcile daemon state before retrying.
- [ ] Verify missing-resource/registry failures are visible. Confirm the application owns authentication, credential storage and access control; the loopback sample is not a production security boundary.
- [ ] If claiming Compose integration, validate the application's external Compose integration and document its dependency and limitations. The SDK does not implement Compose orchestration.

## Publication checks

- [ ] Review migration notes and API snapshot changes, merge the approved MR, and verify checks on the intended release commit.
- [ ] Create the candidate tag only when outstanding gates are resolved or the owner explicitly revises release scope. Do not move existing tags or republish a Maven version.
- [ ] Release CI succeeds, including publication; GitHub release is marked prerelease for 1.0.0-rc.
- [ ] Resolve all four published Maven artifacts (common/JVM/JS/Linux X64) from a fresh consumer and record the exact coordinates.

## Evidence

- Local compatibility validation: `./gradlew build :lib:checkKotlinAbi :lib:dokkaGenerateHtml --warning-mode=fail --console=plain --max-workers=2` passed, including all 218 library tests and sample builds.
- Follow-up compatibility fix: actual Temurin 17 `:lib:allTests :lib:updateKotlinAbi` passed with 224 tests (JVM 102, Node.js 61, Linux X64 61), no failures/skips; separate `:lib:checkKotlinAbi` passed. Both used `--warning-mode=fail`. The ABI diff is limited to the documented logger type and storage metadata nullability changes.
- Matrix run URL/SHA: pending after fixing Java 21 logger bytecode on JDK 17 and nullable Docker 29 containerd metadata discovered by run 34700163603.
- ABI check and negative probe: passed locally. A simulated removed ImagePushResult getter in the reference snapshot failed checkKotlinAbi; restoring the snapshot restored success. JVM and JS/Linux KLib snapshots are committed.
- Dashboard application run: pending.
- Daemon restart/resubscription: deferred.
- Publication: not performed.

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance`.
