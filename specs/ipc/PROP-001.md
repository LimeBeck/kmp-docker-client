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

### Interactive output and ownership {#exec.streams}
- `ExecSession.incomingChunks` delivers binary output as soon as bytes arrive, including TTY prompts without a newline, CR/ANSI sequences, and bytes received with the HTTP upgrade headers.
- Non-TTY chunks carry stdout/stderr identity with Docker framing removed. Chunk boundaries are arbitrary, including within UTF-8 characters; consumers must use a streaming decoder when converting to text. Individual chunks are bounded to 16 KiB.
- Truncated multiplex headers/payloads and transport failures terminate collection with an exception; clean EOF completes normally. Cancellation and consumer exceptions propagate unchanged.
- A session owns its raw connection. Exactly one collection of either `incomingChunks` or the compatibility `incoming` flow is allowed. Completion, cancellation, or failure closes the connection; explicit `close()` is idempotent and is required if output is never collected. Sending after close fails.
- `startInteractive` defaults to TTY for compatibility and accepts an explicit `tty` matching the exec creation config.
- `incoming` retains its existing line-oriented TTY behavior. Terminal applications must use `incomingChunks`, not read `connection.read` directly.
- A cancelled handshake closes the acquired connection and propagates cancellation. Prefix forwarding is scoped to collection, with no detached forwarding job.

### Dashboard terminal sizing {#exec.dashboard-sizing}
- Attach and exec terminal panels fit their available space on initial connection, container layout changes, browser resize, and fullscreen transitions. Changed row/column counts are forwarded to the matching Docker TTY resize endpoint; non-TTY attach skips Docker resize.
- The shared panel offers browser fullscreen with a visible exit button and supports the browser's normal Escape behavior. If the browser rejects fullscreen, the panel fills the viewport with an explicit exit button and Escape support.
- Dashboard WebSocket input uses binary UTF-8 frames; text frames carry JSON resize controls with integer rows/cols in 1..1000. Control frames never reach shell stdin.
- Navigation disposes the terminal, socket, resize observer, animation frame, and document/window listeners.

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

### Stream limits and recovery {#errors.streams.recovery}
- JSON stream records and TTY log lines are limited to 1,048,576 characters; multiplex log payloads are limited to 1,048,576 bytes before allocation. Oversized records and invalid/truncated multiplex framing fail collection and release the response.
- Stream readers apply backpressure without an internal output queue. Idle streams have no request-duration or socket-idle timeout; callers own cancellation/deadlines.
- Stats reject malformed JSON. Events continue to skip malformed JSON records. Complete final JSON records without a newline are accepted; transport failures must propagate even at EOF.
- Docker `uint64` and `uint32` counters must generate Kotlin `ULong` and `UInt`, including nested stats fields, so CPU/memory/network counters do not overflow 32-bit signed integers.
- CIO reports a disconnect between complete HTTP chunks as EOF, even without a terminal zero chunk. Applications must therefore handle both EOF and exceptions when recovering a live subscription.
- Clean EOF completes normally. There is no implicit retry on errors or EOF. Each new collection opens a new stream request; after daemon recovery an application can resubscribe explicitly.
- Event recovery should resume from a saved timestamp with overlap/deduplication and refresh resource state, since event history is finite. Logs need an explicit since/tail policy; stats may simply resubscribe. Cancellation and downstream errors must not trigger retries.

## Changelog {#changelog}
- 2026-09-12: bounded stream records, validated HTTP body completion, and documented explicit resubscription.
- 2026-09-11: defined binary terminal output, single-collection ownership, and session cleanup.
- 2026-09-11: defined image progress completion and cold-stream HTTP error/cancellation semantics after review.
- 2026-03-07: initial API-surface spec extracted from implemented modules.
