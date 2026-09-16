# FEAT-001: Initial Expansion Roadmap for Missing Docker Domains {#root}

Status: ACTIVE — owner resumed missing Docker API support on 2026-09-16
Module URI: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-001.md`

## Goal {#goal}
Define phased expansion for Docker API groups currently outside implemented baseline.

## Remaining domains {#baseline.gaps}
- Plugin

## Phase plan {#phases}

Priority: these expansion phases follow `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#goal`; they are not gates for version 1.0.0.

### Phase A: Discovery and model readiness {#phases.a}
- Validate OpenAPI sections for each missing domain.
- Define minimal viable operations for each domain (list/inspect/create/delete where applicable).
- Document auth/permission constraints for daemon-managed resources.

### Phase B: First implementation slice {#phases.b}
Target small, high-signal operations first:
1. `client.swarm.secrets`: list/inspect/create/update/remove
2. `client.swarm.configs`: list/inspect/create/update/remove
3. Owner expanded scope on 2026-09-16: complete all Swarm API 1.51 endpoints together.

### Phase C: Orchestration domains {#phases.c}
- Cluster init/inspect/join/leave/update/unlock-key/unlock; node list/inspect/update/remove
- Service list/inspect/create/update/remove/logs; task list/inspect/logs
- streaming and update semantics
- conflict handling and eventual consistency guarantees

## Acceptance criteria for each new domain {#acceptance}
1. Dedicated API class under `lib/src/commonMain/.../api/`.
2. Unit/integration tests mirroring style of existing API tests.
3. URI-referenced spec anchors for implemented behavior.
4. WAL entry indicating completion status and next unresolved constraints.

## Swarm resource grouping {#swarm.resources}
The owner requires Swarm APIs under client.swarm. Secrets and Configs use cached child groups,
the configured client endpoint and existing Result/diagnostic behavior. No Swarm init/join occurs
implicitly. Updates require the caller's ULong object version and preserve optimistic concurrency;
only labels may change for secrets/configs. Service, node and cluster updates replace their specs. Secret data is write-only, config data may be returned, and Base64 data
is passed through without automatic encoding. Real tests use a disposable Docker-in-Docker
manager and worker, never host Swarm initialization, on each supported runtime. Lifecycle actions
are explicit; no implicit force, credential rotation, convergence waiting or retry. Logs use inspected
TTY framing, cold backpressured flows and existing cancellation/error semantics. Plugin APIs are independent of Swarm.

## Decisions pending {#decisions}
- Next independent domain after Swarm: Plugin (not part of this change).

## Changelog {#changelog}
- 2026-09-11: deferred domain expansion behind the accepted single-host dashboard readiness goal.
- 2026-03-07: initial roadmap spec drafted from README implementation matrix.
