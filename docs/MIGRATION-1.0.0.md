# Migration to 1.0.0

Stable 1.0.0 is published on Maven Central with the same public API as 1.0.0-rc; no source migration from the published RC is required.

## From 0.1.0

Existing `containers.create(config = ContainerConfig(...))` and image create/push/load calls without callbacks retain their signatures. Use `ContainerCreateRequest` when creating containers with ports, mounts or network attachments; see [container lifecycle](CONTAINER-LIFECYCLE.md). Persist desired configuration in the application, and remove named volumes only through an explicit data-deletion operation.

Image operations now have suspending progress callbacks, with final completion/error still returned as `Result`. Long-operation request/socket-idle timeouts are disabled; use your own coroutine cancellation or deadline. Cancellation releases the request but cannot roll back work already performed by the daemon. See [image progress](IMAGE-PROGRESS.md).

The Docker 29 containerd image store can return `GraphDriver.Data: null`. `DriverData.data` is now nullable; use safe access or `orEmpty()` when appropriate. Non-null metadata is preserved.

`DockerClient.logger` now uses Ktor’s logger type instead of `dev.limebeck.libs.logger.Logger`, removing a runtime dependency compiled for Java 21. Direct users of this companion property must use the Ktor logging API (string messages instead of the old lambda API). Existing logging redaction tests still apply.

Code written against early development commits before the published RC needs these changes:

- Replace `ImageProgress(raw)` with explicit `ImageProgress<TAux>(...)` construction.
- Use `progressDetail?.current` and `progressDetail?.total`, not top-level count getters.
- Push handlers receive `ImageProgress<ImagePushResult>`; read `aux?.tag`, `aux?.digest` and `aux?.size` directly.
- Create/load handlers receive `ImageProgress<Unit>`; inspect `status`/`stream` and the final Result. No aux ID is guaranteed for those operations.
- Invalid typed progress values return an error instead of being treated as missing fields. Unknown fields are ignored by the default JSON configuration.

## From 0.0.x

The changes below were introduced in 0.1.0 and still apply:

- Docker unsigned counters use `UInt`/`ULong`. Update arithmetic and JSON handling to preserve precision; avoid converting large counters to floating point, including on Node.js.
- Interactive `ExecSession.incomingChunks` delivers bytes promptly, including prompts without newlines. Chunks can split UTF-8; use incremental text decoding.
- Collect a session's output once. Completion, cancellation or a consumer exception closes its connection. Explicitly close a session if output is never collected; `first()`/`take()` also close it. Do not bypass ownership through raw channel access or the removed `prependLeftover` helper.
- Log/stat/event collection can fail after preparation succeeds. Catch `DockerApiException` for HTTP failures during collection; cancellation and consumer exceptions propagate. Oversized/truncated records fail rather than grow memory indefinitely.
- A successful HTTP status alone does not prove an image operation succeeded: progress records can contain a Docker error.

The isolated JVM/Docker 29 restart acceptance test verifies old-stream termination and explicit application resubscription. The SDK does not automatically retry or reconnect. Handle EOF as well as transport failures, reconcile current state, and open new terminal sessions without replaying commands. See [stream recovery](STREAM-RECOVERY.md) for the tested scope and application policy.

For the supported runtime combinations and post-1.0 policy, see [compatibility](COMPATIBILITY.md).

## 1.0.1

`DockerClient` now implements Kotlin `AutoCloseable`. Replace manual `try/finally { docker.client.close() }` with `DockerClient(...).use { docker -> ... }`, or call `docker.close()` for an application-owned client. Existing `docker.client.close()` calls remain valid. Close raw exec/attach sessions separately and stop collectors before closing the client. The new method initiates HTTP client shutdown; it does not wait for in-flight requests. This addition is not available in the published 1.0.0 artifacts.

### Connection diagnostics (since 1.0.1)

Optional extensions in `dev.limebeck.libs.docker.client.diagnostics` add `diagnoseConnection` and `diagnoseFailure`, returning `ConnectionDiagnostic` with a `ConnectionProblem` category. Existing request/stream exceptions and Result signatures are unchanged. Cancellation propagates; these helpers neither retry nor reconnect. These additions are included in published 1.0.1.

The published diagnostic report intentionally omits the development-only `endpoint` and `cause` fields: socket paths and exception graphs can contain credentials or private host details. UI/log output should use the SDK-produced report, not the original exception. Diagnostic probes bypass SDK HTTP logging.

### Exception context (since 1.0.1)

The SDK adds a suppressed `DockerContextException` carrying safe operation metadata while retaining original exception types/causes. Read `Throwable.dockerContext`; do not parse messages or assume suppressed exceptions are empty. Cancellation remains unannotated. `DockerApiException.message` no longer embeds raw daemon text (read its existing `error` property in trusted code). Non-HTTP exec/attach handshake failures now throw the original exception instead of reducing it to an ErrorResponse string. SDK HTTP error Results and image progress failures retain context through map/mapError. Their getOrThrow now raises DockerResultException (still an IllegalStateException), exposing the original error through its error property and the cause when available. Caller-created Results may remain unannotated.

## 1.1.0

Adds `dev.limebeck.libs.docker.compose` inside the existing artifact: import
`dev.limebeck.libs.docker.compose.compose` to discover existing Compose projects
and stream their logs. Existing Engine APIs and client ownership remain unchanged.
See [Compose guide](COMPOSE.md) for multi-service selection, one-offs and stream limits.
No Compose YAML parser or CLI dependency is introduced in the SDK.
