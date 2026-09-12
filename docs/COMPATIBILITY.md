# Compatibility policy

This document describes the upcoming 1.0.0-rc. It does not claim that the release candidate has passed every acceptance gate or has been published.

## Supported scope and CI matrix

The target is a single Linux Docker host accessed through a Unix domain socket. Requests use Docker API **1.51**, with no automatic version negotiation. Older daemons that cannot serve 1.51 are unsupported. TCP/TLS, Windows named pipes, browser JavaScript, macOS native binaries, ARM native binaries, Swarm and multi-host orchestration are outside this release target.

PR CI on Ubuntu 24.04 X64 runs the Cartesian product below. Every cell builds and tests the JVM, Node.js and Linux X64 targets, checks ABI, builds samples and generates Dokka:

| Docker Engine | Temurin JDK | Kotlin/JS Node.js | Kotlin/Native |
| --- | --- | --- | --- |
| 28.5.2 | 17 | 24.16.0 | Linux X64 |
| 28.5.2 | 21 | 24.16.0 | Linux X64 |
| 29.0.0 | 17 | 24.16.0 | Linux X64 |
| 29.0.0 | 21 | 24.16.0 | Linux X64 |

These are pinned regression targets, not a promise that every Docker patch release is tested. Docker 28.5 supports API 1.51; Docker 29.0 accepts it within its supported API range ([Docker API matrix](https://docs.docker.com/reference/api/engine/)). The workflow verifies `/v1.51/_ping` before testing. A compatible version range alone is not evidence that behavioral tests passed; inspect all four PR check results before merging.

The library emits JVM 17 bytecode. The build uses Kotlin 2.4.20 and Ktor 3.5.2. Consumer builds with older Kotlin compilers or other Ktor versions are not covered by this matrix. Kotlin/JS is tested under Node.js, not a browser. Native support refers to the Linux X64 target; other operating systems and architectures require separate validation. Release CI retains Docker 28.5.2 / JDK 21.

## Public API baseline

The Kotlin Gradle Plugin generates reference ABI dumps at `lib/api/lib.api` (JVM) and `lib/api/lib.klib.api` (JS/Linux X64 KLib). The built-in ABI DSL currently requires an explicit experimental opt-in. Generated OpenAPI models are part of the public API and are included. Run:

```sh
./gradlew :lib:checkKotlinAbi --warning-mode=fail --console=plain --max-workers=2
```

The same check is attached to `check`/`build` and runs explicitly in PR CI. On a host that cannot build all targets, ABI validation must fail instead of inferring a missing platform's ABI. Linux X64 is the baseline-maintenance host.

For an intentional API change, inspect the source and generated-model diff, assess migration impact, then regenerate locally:

```sh
./gradlew :lib:updateKotlinAbi --warning-mode=fail --console=plain --max-workers=2
```

Commit the reviewed reference diff with the change and migration notes. CI must never refresh the baseline automatically. The initial candidate baseline records the current API; it does not retroactively prove binary compatibility with 0.1.0. The checker also flags additions, so not every detected difference is a breaking change. Kotlin documents the mechanism in [binary compatibility validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html).

Snapshots do not prove behavior, JSON wire compatibility, transitive dependency compatibility or every Kotlin source-compatibility case. Changes to defaults, serialization names, nullability, coroutine ownership or generated schemas require tests and review even when the ABI check passes.

## Versioning after 1.0.0

Patch releases fix defects while preserving supported public API and documented behavior. Minor releases may add API; removal or incompatible changes require a major release. Before removing an API, provide a deprecated replacement and migration guidance in a minor release. Changes to generated public models count as API changes, not an implementation exception. Pre-1.0 and RC changes may still require migration and must be documented.

Kotlin, Ktor, coroutine and serialization upgrades need the same ABI and behavior review. The project does not guarantee arbitrary mixes of dependency versions. Keep the library's dependency set aligned until another combination has been tested.

## Warning policy

Kotlin compilation treats warnings as errors across library and samples, including generated code compilation. Gradle checks use `--warning-mode=fail`; Dokka uses `failOnWarning`. Do not blanket-suppress warnings to make CI green.

The OpenAPI generator currently prints diagnostics about unnamed `allOf` schemas while generating Docker's schema. These are generator diagnostics, not compiler or Gradle deprecations; changing schema/model generation requires API review and new snapshots. This known limitation is separate from a successful warning-as-error compilation.

Contracts: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#ci.pr` and `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#ci.abi`.
