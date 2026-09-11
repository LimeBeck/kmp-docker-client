# FEAT-001: Initial Expansion Roadmap for Missing Docker Domains {#root}

Status: DRAFT — deferred behind the accepted single-host 1.0.0 goal
Module URI: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-001.md`

## Goal {#goal}
Define phased expansion for Docker API groups currently outside implemented baseline.

## Out-of-scope baseline (today) {#baseline.gaps}
- Swarm
- Node
- Service
- Task
- Secret
- Config
- Plugin

## Phase plan {#phases}

Priority: these expansion phases follow `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#goal`; they are not gates for version 1.0.0.

### Phase A: Discovery and model readiness {#phases.a}
- Validate OpenAPI sections for each missing domain.
- Define minimal viable operations for each domain (list/inspect/create/delete where applicable).
- Document auth/permission constraints for daemon-managed resources.

### Phase B: First implementation slice {#phases.b}
Target small, high-signal operations first:
1. `Secret` list/create/remove
2. `Config` list/create/remove
3. `Service` list/inspect

### Phase C: Orchestration domains {#phases.c}
- Node/Task/Swarm advanced workflows
- streaming and update semantics
- conflict handling and eventual consistency guarantees

## Acceptance criteria for each new domain {#acceptance}
1. Dedicated API class under `lib/src/commonMain/.../api/`.
2. Unit/integration tests mirroring style of existing API tests.
3. URI-referenced spec anchors for implemented behavior.
4. WAL entry indicating completion status and next unresolved constraints.

## Decisions pending {#decisions}
- Selection priority between Service vs Secret/Config for first implementation PR.
- Required test strategy against local Docker daemon in CI.

## Changelog {#changelog}
- 2026-09-11: deferred domain expansion behind the accepted single-host dashboard readiness goal.
- 2026-03-07: initial roadmap spec drafted from README implementation matrix.
