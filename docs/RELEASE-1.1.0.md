# Release 1.1.0

Owner authorized release on 2026-09-15. Additive Compose APIs use a minor version.

## Scope

- Label-based Compose discovery and bounded multi-service log streaming inside lib.
- Dashboard host overview, Compose resource views and network topology with container inspector.
- Existing JVM 17+, NodeJS and Linux X64 targets; fixed Docker API 1.51.
- No Compose lifecycle mutations, YAML orchestration or new runtime dependency.

## Gates

- Final MR #11 head must pass all four Docker/JDK CI cells, ABI and Dokka checks.
- Native dashboard build and read-only/disposable dashboard checks must pass.
- Merge validated head, verify the merge tree, create immutable v1.1.0 once.
- Wait for Release CI publication and verify all Maven variants independently.
- Installation examples use 1.1.0 at the owner’s request; retain a publication-in-progress note until artifacts resolve.

Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance.
