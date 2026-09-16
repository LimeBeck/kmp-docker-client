# Release 1.3.0

Owner authorized publication on 2026-09-16. Additive Swarm APIs use a minor version.

## Scope

- All 30 Swarm endpoints in Docker API 1.51 under `client.swarm`: cluster lifecycle,
  nodes, services, tasks, secrets and configs, including service/task logs and rollback.
- Existing Compose container start/stop/restart controls and dashboard improvements.
- JVM 17+, NodeJS and Linux X64; fixed Docker API 1.51 over Unix sockets.

## Gates

- Final MR head passes the four Docker/JDK PR matrix cells, ABI and Dokka.
- Isolated Swarm manager/worker lifecycle tests pass on JVM, NodeJS and Linux X64;
  JVM additionally verifies manager restart/unlock. Existing SDK/Compose/recovery gates remain.
- Merge the validated tree, create immutable v1.3.0 once, complete Release and Docs CI.
- Resolve all Maven variants in an independent consumer and attach the built dashboard executable
  to the GitHub release. Never republish an existing Maven version or move its tag.

## Local evidence

23 Swarm tests passed across supported runtimes, including wire contracts, cancellation,
version conflicts, service rollback, worker membership, TTY/multiplexed logs and manager unlock.
ABI, Dokka, compiled guide examples and diagnostic regression passed.

Contract: spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#publish.
