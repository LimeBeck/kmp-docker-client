# kmp-docker-client IPC Specs Index {#root}

Base URI namespace:
`spec://io.github.limebeck.kmp-docker-client/specs/ipc/<DOC>#<anchor>`

## Core specs {#core}
- [`PROP-000.md`](./PROP-000.md) — foundational architecture and invariants.
- [`PROP-001.md`](./PROP-001.md) — current API surface and behavioral contracts.
- [`PROP-002.md`](./PROP-002.md) — CI/CD release pipeline contract.
- [`FEAT-001.md`](./FEAT-001.md) — initial expansion roadmap for missing Docker domains.

- [`FEAT-002.md`](./FEAT-002.md) — accepted single-host dashboard readiness goal and 1.0.0 gates.

## Usage rule {#usage}
For new implementation work:
1. Link task to one or more explicit spec URIs.
2. Implement atomically with tests.
3. Update `WAL.md` and (if needed) spec changelog sections.
