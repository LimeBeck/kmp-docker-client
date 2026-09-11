# PROP-001: Current API Surface and Behavioral Contracts {#root}

Status: ACTIVE  
Module URI: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md`

## Acceptance snapshot (read first) {#acceptance}
Library currently guarantees functional API groups for:
- Containers
- Images
- Networks
- Volumes
- Exec
- System
- Auth

Anything outside this set is out of active support scope in current baseline.

## API grouping contract {#api.groups}
`DockerClient` exposes cached DSL APIs grouped by Docker domains.  
Each group must return typed `Result<*, ErrorResponse>` for request/response operations, except streaming functions that may return `Flow<...>`.

## Containers behavior {#containers}
Must provide:
- list/inspect/create/start/stop/restart/kill/remove/rename/pause/unpause/wait
- logs streaming with TTY-aware frame parsing
- stats and events-related calls where already implemented

### Logs stream rules {#containers.logs}
- log stream must preserve source channel type (`stdout`/`stderr` where available)
- follow/timestamps/since/until/tail parameters are passed through
- implementation must apply connection config before execution

## Images behavior {#images}
Must provide pull/list/inspect/remove/prune and related distribution flows already present in code.

### Pull auth rules {#images.auth}
- image pull must apply registry auth header via `X-Registry-Auth` when matching auth exists.
- docker hub aliases and registry variants must be resolved using priority candidates.

### Image operation completion {#images.progress}
- Pull, push, and load must consume the NDJSON progress response through completion.
- HTTP success alone is insufficient: `errorDetail.message` or `error` in progress must return `Result.error(ErrorResponse)`.
- Malformed progress messages must report an error; cancellation must propagate.

## Networks behavior {#networks}
Must provide create/list/inspect/remove/connect/disconnect/prune operations.

## Volumes behavior {#volumes}
Must provide create/list/inspect/remove/prune operations.

## Exec behavior {#exec}
Must support command execution lifecycle including interactive session/hijack flows where implemented.

## System behavior {#system}
Must provide:
- info/version/ping/data usage
- events streaming as `Flow<EventMessage>` with line-by-line decode and invalid-line skip.

## Error handling and resilience {#errors}
- Any non-success HTTP response should map to `ErrorResponse`.
- Event/log streams should tolerate malformed lines without terminating stream processing globally.

### Cold stream failures {#errors.streams}
- Logs, streaming stats, and events remain cold: the streaming HTTP request opens on collection and closes on completion or cancellation.
- Non-success streaming HTTP responses must be decoded into `ErrorResponse` and thrown as `DockerApiException` carrying that error and HTTP status before any data is emitted.
- A `Result<Flow<...>, ErrorResponse>` only describes preparation; errors when collecting its Flow follow the rule above.
- Events may skip malformed JSON records, but must propagate downstream exceptions and cancellation unchanged.

## Changelog {#changelog}
- 2026-09-11: defined image progress completion and cold-stream HTTP error/cancellation semantics after review.
- 2026-03-07: initial API-surface spec extracted from implemented modules.
