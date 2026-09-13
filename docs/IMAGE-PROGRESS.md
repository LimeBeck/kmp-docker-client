# Image operation progress

Available since the published `1.0.0-rc` and included in the upcoming stable `1.0.0`. Existing convenience overloads remain available.

```kotlin
val result = client.images.create("alpine:latest") { update ->
    println("${update.id.orEmpty()}: ${update.status.orEmpty()} ${update.progressDetail?.current}/${update.progressDetail?.total}")
}
result.fold(
    onSuccess = { println("Pull completed") },
    onError = { error -> println("Pull failed: ${error.message}") },
)
```

The callback type is determined by the operation:

| Operation | Callback record | Auxiliary data |
| --- | --- | --- |
| `create` (pull/import) | `ImageProgress<Unit>` | No documented auxiliary payload |
| `push` | `ImageProgress<ImagePushResult>` | Optional `tag`, `digest`, `size` |
| `load` | `ImageProgress<Unit>` | No documented auxiliary payload |

```kotlin
client.images.push("registry.example.com/app", tag = "latest") { update ->
    update.aux?.let { result -> println("${result.digest}: ${result.size} bytes") }
}.getOrThrow()
```

`ImageProgress<TAux>`, `ImageProgressDetail` and `ImagePushResult` are serializable data classes; no public progress field uses JSON objects. Push result fields map to Docker's `Tag`, `Digest`, `Size` wire names. `aux` is optional and its absence does not imply failure. Pull/load results arrive through `status`/`stream`; do not infer image IDs from an undocumented aux payload. The generic model also supports serialization with other explicitly chosen auxiliary types.

Counts in `progressDetail.current`, `progressDetail.total` and push `aux.size` are exact unsigned integers, including on Node.js. Missing counts remain null; invalid counts fail the operation as malformed progress. Unknown JSON fields are ignored by default. Layer counts are not an overall percentage. Render unknown totals as indeterminate progress, and avoid dividing by zero.

The push wire format follows [Docker 28.5.2 PushResult](https://github.com/moby/moby/blob/v28.5.2/api/types/types.go#L99).

Callbacks run sequentially as records arrive. A suspending callback applies backpressure; it does not create an unbounded queue. The library does not choose a UI dispatcher, so switch to the appropriate dispatcher before updating UI state. Bound any application-owned progress history.

Success is the returned successful `Result` after response completion. An HTTP 200, a completed layer, or a status containing “done” is insufficient. Docker error records, malformed JSON and detectable truncated responses return an error. HTTP failures return the daemon error without emitting progress. Connection establishment failures may throw transport exceptions, as with other client requests. Cancellation and exceptions thrown by your callback propagate unchanged.

Use a coroutine job or `withTimeout` to bound an operation. The request has no duration or socket-idle timeout while waiting for progress. Cancelling the job closes the response, including cancellation while the callback is suspended. Docker may already have changed local images or pushed layers: cancellation is not rollback. Inspect daemon/registry state before retrying. Loading consumes its archive channel once; supply a fresh channel for a new attempt.

CIO can report a disconnect between complete HTTP chunks as normal EOF even when a terminating chunk is missing. The client detects short Content-Length responses and reported transport failures, but cannot prove completion in that ambiguous case. Applications requiring confirmation should inspect the expected image/digest after completion.

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#images.progress`.
