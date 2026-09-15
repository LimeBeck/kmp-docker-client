# FEAT-002: Single-Host Dashboard Readiness for 1.0.0 {#root}

Status: ACCEPTED ROADMAP (not a claim of current readiness)
Module URI: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md`

## Goal {#goal}
Version 1.0.0 must reliably support a management panel for one Docker host.
This target was accepted by the project owner. Complete Docker API coverage is not a release requirement.

## Boundary {#boundary}
- The library owns Docker API access, registry authentication, transport/session lifecycle, typed errors, and streaming behavior.
- The application owns user authentication, roles, credential storage, audit trails, HTTP/WebSocket access control, and orchestration policy.
- Unix domain sockets remain the baseline. Multi-host management, TCP/TLS transport, Swarm, and Windows named pipes are outside this 1.0.0 target.
- The sample dashboard is a development example, not a production authentication boundary.
- Compose project management belongs in an application/integration layer. Readiness requires validating the Docker operations that layer needs, not implementing a Compose engine inside the SDK.

## Priorities {#priorities}
1. Reliable interactive terminal: byte/chunk delivery without waiting for a newline, stdout/stderr framing where applicable, resize, cancellation, and session cleanup.
2. Reliable logs, stats, and events: bounded resource use, explicit failure/completion behavior, cancellation, daemon restart, and a documented application resubscription strategy.
3. Container lifecycle workflows: ports, mounts, environment, network attachment, recreate/update workflows, and explicit protection of persistent data.
4. Observable long operations: pull/push/load progress available to consumers, terminal success/error state, and cancellation semantics.
5. Stable API and release guarantees: consistent errors, connection ownership, documented compatibility, PR CI, and reproducible Maven Central publication.

## Milestones {#milestones}
- 0.1.0: released terminal/session ownership, bounded logs/stats/events, unsigned counters, and dashboard terminal sizing.
- 1.0.0-rc: one release combining the remaining single-host readiness work. Implement it in the ordered steps below; do not publish intermediate feature releases.
- 1.0.0: release only after the acceptance gates below pass and the release candidate has passed SDK integration tests against real Docker. Milestones describe scope, not delivery dates.

### Ordered release-candidate work {#milestones.rc}
1. Stabilize timing-sensitive tests, establish PR CI, and synchronize release documentation.
2. Validate create/start/inspect/recreate/delete with ports, environment, network attachment, and persistent volumes; retained data is an explicit assertion.
3. Expose pull/push/load progress, final success/error, and cancellation to consumers.
4. Validate stream termination and application resubscription after restart of an isolated Docker daemon, including repeated session cleanup. The owner resumed the remaining RC work on 2026-09-12. The isolated JVM/Docker 29 acceptance test now covers old-stream termination, explicit application resubscription, state reconciliation and repeated terminal socket cleanup; see docs/STREAM-RECOVERY.md.
5. Establish the supported Docker/platform CI matrix, public API compatibility baseline, migration guidance, and an SDK acceptance checklist.
6. Run the complete acceptance suite before publishing 1.0.0-rc. Preparing the candidate does not itself create a release tag.

### Stable release preparation {#milestones.stable}
- Prepare 1.0.0 with the published RC API, migration/compatibility guidance, and a Dokka-integrated user guide whose marked Kotlin examples compile.
- Validate the final preparation commit using the existing SDK matrix, ABI and warning checks. Tagging/publication remain a separate owner-authorized action.
- Track preparation and publication evidence in docs/RELEASE-1.0.0.md.

## Acceptance gates {#acceptance}
- SDK behavior is verified directly with Kotlin tests and real Docker. Dashboard UI, HTTP/WebSocket routes, forms, HTMX and fullscreen behavior are outside library release gates; the sample may be smoke-tested independently.
- Kotlin SDK integration tests validate creating a container with ports and a persistent volume, starting it, opening a terminal, observing logs/stats, recreating it with changed configuration, and deleting the container while retaining the data unless volume deletion was explicitly requested.
- An application-level Compose integration can use the supported SDK operations to inspect and manage its single-host resources; any external Compose dependency or unsupported operation is documented.
- Negative scenarios cover missing resources, registry failures, malformed/partial responses, connection loss, daemon restart, consumer failure, and cancellation.
- Streaming consumers receive data promptly; stopping a consumer or closing a session releases the associated connection. Repeated connect/disconnect tests must not show accumulating connections or jobs.
- Supported Docker versions and JVM/Linux X64/NodeJS environments are documented and tested in CI for every PR. Docker API 1.51 remains the baseline until its foundational contract is explicitly revised.
- Public compatibility checks and migration notes cover any breaking changes before 1.0.0; post-1.0 compatibility policy is documented before the stable release.
- Release validation includes successful build/test/publication and resolution of the published artifacts from Maven Central.

## Deprecation cleanup {#maintenance.deprecations}
- Before 1.0.0, audit and resolve deprecation warnings from maintained Kotlin code, Gradle/build plugins, and CI actions.
- Start with deprecated `ByteReadChannel.readUTF8Line` calls; preserve framing, EOF, malformed-input, and cancellation behavior when migrating.
- Track warnings from generated code or external dependencies separately and fix their generator/dependency source where possible. Do not hide warnings with blanket suppression.
- The RC cleanup is complete; retain warning-as-error checks for stable preparation and future maintenance.

## Deferred expansion {#deferred}
The missing-domain exploration in `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-001.md#phases` is deferred behind this goal. It must not displace the single-host readiness work unless the project owner revises the priority.

### macOS and Windows release {#deferred.desktop-platforms}
The owner selected macOS and Windows support for one future release on 2026-09-13. Deliver JVM and Kotlin/Native support for both operating systems together; a JVM-only release does not complete this milestone. The release number and date remain unassigned, and this work does not expand the current 1.0.0-rc acceptance scope.

- Establish the supported CPU/OS matrix, including macOS Apple Silicon/Intel and Windows native target availability, before implementation. Any target limitation must be explicit in the release scope.
- Support local Docker Desktop connections: Unix domain sockets on macOS and Windows named pipes for native Windows processes. WSL2 support alone does not satisfy Windows support.
- Share connection configuration and lifecycle semantics across ordinary HTTP requests and duplex exec/attach transport; preserve cancellation and bounded resource cleanup.
- Port the dashboard and its launch/configuration handling, and document Docker endpoint selection on each OS.
- Require Kotlin SDK integration tests against real Docker on each OS/runtime combination: container lifecycle and retained volumes, image progress/errors, logs/stats/events, terminal UTF-8/resize, disconnect/cancellation and repeated session cleanup.
- Add platform CI, ABI validation and publication/consumer checks for all new native artifacts. Compilation alone is not platform acceptance.
- Distinguish running the client on Windows from managing Windows containers. Decide and document Windows-container coverage separately; do not infer it from Docker Desktop Linux-container tests.

### Diagnostics integration with existing API extensions {#deferred.api-extensions}
API extensions already exist through extension properties and the public api() delegate with ApiCacheHolder/ApiDelegate; built-in API groups use the same mechanism. Preserve this mechanism and its existing usage patterns. The work here is to integrate safe diagnostics with extensions, not to introduce a replacement extension framework. Release number and date remain unassigned.

- Design a way for existing API extensions to supply safe operation metadata, including route templates, without adding their routes to a central SDK allowlist.
- Apply this metadata consistently to ordinary HTTP and exec/attach failures while retaining existing request, serialization, typed-error, cancellation and ownership contracts.
- Define the trust boundary: extension-supplied templates must not contain credentials, actual resource identifiers or query values. Keep `/{unknown}` when safe metadata is absent; do not fall back to exposing raw paths.
- Extend the existing extension example to demonstrate diagnostic metadata. Validate that an external API group receives useful error context without core route changes, and test sensitive-data exclusion and cancellation.
- Preserve compatibility with existing extensions that do not supply metadata; document the opt-in integration and check public API compatibility.

Related contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.context`.

## Changelog {#changelog}
- 2026-09-15: owner clarified that API extensions already exist; plan diagnostic integration with them instead of a new extension mechanism.
- 2026-09-13: owner requested stable preparation and a user guide integrated with Dokka.
- 2026-09-13: owner clarified that SDK releases require library integration tests, not dashboard application acceptance.
- 2026-09-13: owner selected a future release combining macOS and Windows support on both JVM and Kotlin/Native.
- 2026-09-12: owner requested all remaining RC preparation; resumed and validated isolated daemon restart, without restarting the host daemon.
- 2026-09-12: owner deferred daemon-restart work and requested the remaining compatibility and release-readiness work first.
- 2026-09-12: owner consolidated remaining work into one 1.0.0-rc release, implemented step by step.
- 2026-09-11: accepted the single-host management-panel goal and corresponding 1.0.0 readiness gates.
