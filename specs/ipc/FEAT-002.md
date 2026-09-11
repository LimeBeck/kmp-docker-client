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
- 0.1: define lifecycle/error contracts and implement terminal/stream reliability with regression coverage.
- 0.2: validate full single-host application workflows, expose operation progress, and establish the supported Docker/platform test matrix.
- 1.0.0-rc: freeze the intended stable public surface, publish migration guidance, and validate against a real dashboard application.
- 1.0.0: release only after the acceptance gates below pass. Milestones describe scope, not delivery dates.

## Acceptance gates {#acceptance}
- A dashboard can create a container with ports and a persistent volume, start it, open a terminal, observe logs/stats, recreate it with changed configuration, and delete the container while retaining the data unless volume deletion was explicitly requested.
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
- This is follow-up maintenance and does not expand the current 0.0.10 release.

## Deferred expansion {#deferred}
The missing-domain exploration in `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-001.md#phases` is deferred behind this goal. It must not displace the single-host readiness work unless the project owner revises the priority.

## Changelog {#changelog}
- 2026-09-11: accepted the single-host management-panel goal and corresponding 1.0.0 readiness gates.
