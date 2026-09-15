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

## Release evidence

- MR #11 merged at `7f9161bfe02491724cd0b4997396941472b84508`; tree matches validated `ff33da227e0a824619dc1311c2fe4f723da10329`.
- Final PR matrix [35013164308](https://github.com/LimeBeck/kmp-docker-client/actions/runs/35013164308) passed all four cells.
- Immutable tag `v1.1.0` points to the merge commit.
- [Release CI 35014019338](https://github.com/LimeBeck/kmp-docker-client/actions/runs/35014019338) and [Docs CI 35013981163](https://github.com/LimeBeck/kmp-docker-client/actions/runs/35013981163) passed.
- [GitHub Release](https://github.com/LimeBeck/kmp-docker-client/releases/tag/v1.1.0) includes the verified Linux X64 dashboard executable.
- Central Portal accepted automatic publication; public Maven propagation verification is pending.
