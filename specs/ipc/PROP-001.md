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

## Client ownership {#client.ownership}
- DockerClient implements Kotlin AutoCloseable; close() delegates to its owned HttpClient and is idempotent. It initiates shutdown without waiting for active HTTP calls.
- Short-lived examples use .use {}; application-scoped clients close at shutdown after collectors stop. Raw exec/attach sessions retain independent ownership and must be closed separately.

## Containers behavior {#containers}
Must provide:
- list/inspect/create/start/stop/restart/kill/remove/rename/pause/unpause/wait
- logs streaming with TTY-aware frame parsing
- stats and events-related calls where already implemented

### Logs stream rules {#containers.logs}
- log stream must preserve source channel type (`stdout`/`stderr` where available)
- follow/timestamps/since/until/tail parameters are passed through
- implementation must apply connection config before execution

### Container recreation and persistent data {#containers.recreate}
- `create` accepts a complete typed `ContainerCreateRequest`, including `HostConfig` and `NetworkingConfig`. The existing `ContainerConfig` overload remains available without changing its signature.
- Creating a container does not start it or replace a conflicting name. Docker failures are returned as typed errors without implicit cleanup or retry.
- Removing a container defaults to `v=false`. Named-volume deletion requires an explicit volume removal call; recreation must not implicitly prune volumes or delete data.
- Environment, ports, mounts and network configuration are supplied explicitly for each replacement. Orchestration, rollback, readiness checks and backups belong to the application.
- Real-Docker tests on all supported targets cover configuration inspection, conflicting/missing-image errors, resource update, recreation with changed environment, and reading retained volume data after both original and replacement removal.

## Storage metadata compatibility {#models.storage}
- DriverData.Data is nullable: Docker 29 with containerd image storage returns null for container/image inspection. The local schema explicitly corrects this wire compatibility mismatch; preserve non-null driver maps without replacing null with invented metadata.

## Images behavior {#images}
Must provide pull/list/inspect/remove/prune and related distribution flows already present in code.

### Pull auth rules {#images.auth}
- image pull must apply registry auth header via `X-Registry-Auth` when matching auth exists.
- docker hub aliases and registry variants must be resolved using priority candidates.

### Image operation completion {#images.progress}
- Pull, push, and load must consume the NDJSON progress response through completion.
- HTTP success alone is insufficient: `errorDetail.message` or `error` in progress must return `Result.error(ErrorResponse)`.
- Malformed progress messages must report an error; cancellation must propagate.
- Each operation has an overload with a required suspending `onProgress(ImageProgress)` callback; existing signatures remain available. Non-error records are delivered in wire order, without an internal queue, awaiting the callback before reading further records. ImageProgress<TAux>, ImageProgressDetail and ImagePushResult are serializable data classes with explicit nullable fields. Counts are ULong values nested in progressDetail. Push callbacks use ImageProgress<ImagePushResult>, with Tag/Digest/Size mapped to tag/digest/size (ULong). Create and load callbacks use ImageProgress<Unit>, since their supported wire format has no documented aux payload. Aux remains optional; public progress models contain no JsonObject. Unknown JSON fields are ignored; invalid typed fields return an error before the callback.
- The returned `Result<Unit, ErrorResponse>` is the final daemon outcome; intermediate status strings and counts do not imply success. Error records are returned as errors rather than delivered as progress. Detectable transport truncation must not return success.
- Progress records use the same 1 MiB character limit as other JSON streams. Long operations have no request-duration or socket-idle timeout; the caller owns cancellation/deadlines. Cancellation and callback exceptions propagate unchanged and release the response; they never trigger automatic retry.
- Callback cancellation releases the client request but does not guarantee rollback of work already performed by Docker. Load consumes its supplied body once; retry requires a fresh body and reconciliation with daemon state.

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
- Hide the terminal viewport scrollbar only while the alternate screen buffer is active (for applications such as mc/vim); restore it in the normal shell buffer. Preserve scrolling and refit after buffer changes in both normal and fullscreen modes.
- Attach and exec terminal panels fit their available space on initial connection, container layout changes, browser resize, and fullscreen transitions. Changed row/column counts are forwarded to the matching Docker TTY resize endpoint; non-TTY attach skips Docker resize.
- The shared panel offers browser fullscreen with a visible exit button and supports the browser's normal Escape behavior. If the browser rejects fullscreen, the panel fills the viewport with an explicit exit button and Escape support.
- Dashboard WebSocket input uses binary UTF-8 frames; text frames carry JSON resize controls with integer rows/cols in 1..1000. Control frames never reach shell stdin.
- Navigation disposes the terminal, socket, resize observer, animation frame, and document/window listeners.

## System behavior {#system}
Must provide:
- info/version/ping/data usage
- events streaming as `Flow<EventMessage>` with line-by-line decode and invalid-line skip.

## Error handling and resilience {#errors}

### Connection diagnostics {#errors.diagnostics}
- Provide opt-in diagnoseConnection and diagnoseFailure APIs without changing existing operation Result/exception contracts or adding retries.
- Probe the configured socket using the fixed versioned ping, positive per-probe HTTP timeouts and at most 4096 response bytes; close the response after inspection.
- Owner privacy requirement: SDK-produced reports expose only category, fixed summary/suggestion and HTTP status. Do not retain socket paths, original exceptions, response bodies or headers, including via data-class toString/copy/components. Exclude diagnostic probe requests from the SDK HTTP logging plugin. Caller-owned exceptions, cancellation and custom logging are outside the report guarantee.
- Distinguish known missing-socket, access-denied, refused/lost-connection, timeout and explicit API-version-rejection signals. Unknown/localized errors remain unknown; generic HTTP 400/404 is not proof of API incompatibility.
- Propagate caller cancellation unchanged. These APIs do not establish permissions for every endpoint or recover streams; consumers still own reconciliation, resubscription and interpretation of clean EOF.

- Any non-success HTTP response should map to `ErrorResponse`.
- Event/log streams should tolerate malformed lines without terminating stream processing globally.

### Exception operation context {#errors.context}
- Attach safe method, allowlisted route template, API version, failure stage and known HTTP status to exceptions observed at SDK HTTP request/response/stream and exec/attach connect/handshake/read/write boundaries.
- Preserve exception identity/type and cause; use a suppressed DockerContextException marker and a dockerContext accessor that follows bounded, cycle-safe cause chains for coroutine stack recovery. Do not annotate caller cancellation.
- Context omits endpoint paths, identifiers, query values, headers and payloads. Original exceptions/causes and raw daemon error fields remain for trusted debugging and are not safe UI/log output.
- DockerApiException.message contains numeric status only; error retains raw daemon details. Non-HTTP handshake failures propagate with context instead of being reduced to an ErrorResponse string. HTTP handshake rejections remain error Results.
- SDK HTTP error Results, image progress failures and HTTP handshake rejections retain operation context through map/mapError and getOrThrow. getOrThrow raises DockerResultException (an IllegalStateException) with safe message, raw error property and retained cause when available. Arbitrary caller-created Results and raw channels consumed outside SDK boundaries may have no context.

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
- 2026-09-12: corrected nullable storage metadata for Docker 29 and removed the library logger dependency requiring Java 21.
- 2026-09-12: owner requested generic operation-specific aux; push now uses ImagePushResult, create/load use Unit, with no JSON fields in public progress models.
- 2026-09-12: owner requested explicit serializable image progress models instead of a raw JSON wrapper; invalid counts are rejected rather than treated as absent.
- 2026-09-12: exposed ordered image progress callbacks while retaining final Result outcomes and existing signatures.
- 2026-09-12: added the complete container creation request and persistent-data recreation contract for the release candidate.
- 2026-09-12: bounded stream records, validated HTTP body completion, and documented explicit resubscription.
- 2026-09-11: defined binary terminal output, single-collection ownership, and session cleanup.
- 2026-09-11: defined image progress completion and cold-stream HTTP error/cancellation semantics after review.
- 2026-03-07: initial API-surface spec extracted from implemented modules.
