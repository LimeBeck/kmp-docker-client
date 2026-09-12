# Migration to 1.0.0-rc

The candidate is under development; published 0.1.0 remains the installation baseline until the release tag and Maven artifacts exist.

## From 0.1.0

Existing `containers.create(config = ContainerConfig(...))` and image create/push/load calls without callbacks retain their signatures. Use `ContainerCreateRequest` when creating containers with ports, mounts or network attachments; see [container lifecycle](CONTAINER-LIFECYCLE.md). Persist desired configuration in the application, and remove named volumes only through an explicit data-deletion operation.

Image operations now have suspending progress callbacks, with final completion/error still returned as `Result`. Long-operation request/socket-idle timeouts are disabled; use your own coroutine cancellation or deadline. Cancellation releases the request but cannot roll back work already performed by the daemon. See [image progress](IMAGE-PROGRESS.md).

Code written against earlier commits in this unpublished RC branch needs these changes:

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

Daemon restart recovery remains a deferred acceptance gate. The SDK does not automatically retry or reconnect. Existing stream completion/cancellation contracts remain unchanged; deferral is not a claim of recovery support.

For the supported runtime combinations and post-1.0 policy, see [compatibility](COMPATIBILITY.md).
