# Image operation progress

Available in the upcoming `1.0.0-rc`. Existing calls without a callback keep their signatures and still wait for completion.

```kotlin
val result = client.images.create("alpine:latest") { update ->
    println("${update.id.orEmpty()}: ${update.status.orEmpty()} ${update.progressDetail?.current}/${update.progressDetail?.total}")
}
result.fold(
    onSuccess = { println("Pull completed") },
    onError = { error -> println("Pull failed: ${error.message}") },
)
```

`images.push(name, onProgress = { ... })` and `images.load(body = archive, onProgress = { ... })` use the same callback and final result. The callback receives `ImageProgress` with optional `id`, `status`, `stream`, `progress`, `progressDetail` and `aux`. Both `ImageProgress` and `ImageProgressDetail` are serializable data classes with explicit fields; only the extensible `aux` payload uses `JsonObject`. Counts in `progressDetail.current` and `progressDetail.total` are exact unsigned integers, including on Node.js. Missing counts remain null; invalid counts fail the operation as malformed progress. Unknown JSON fields are ignored. Layer counts are not an overall percentage. Render unknown totals as indeterminate progress, and avoid dividing by zero.

Callbacks run sequentially as records arrive. A suspending callback applies backpressure; it does not create an unbounded queue. The library does not choose a UI dispatcher, so switch to the appropriate dispatcher before updating UI state. Bound any application-owned progress history.

Success is the returned successful `Result` after response completion. An HTTP 200, a completed layer, or a status containing “done” is insufficient. Docker error records, malformed JSON and detectable truncated responses return an error. HTTP failures return the daemon error without emitting progress. Connection establishment failures may throw transport exceptions, as with other client requests. Cancellation and exceptions thrown by your callback propagate unchanged.

Use a coroutine job or `withTimeout` to bound an operation. The request has no duration or socket-idle timeout while waiting for progress. Cancelling the job closes the response, including cancellation while the callback is suspended. Docker may already have changed local images or pushed layers: cancellation is not rollback. Inspect daemon/registry state before retrying. Loading consumes its archive channel once; supply a fresh channel for a new attempt.

CIO can report a disconnect between complete HTTP chunks as normal EOF even when a terminating chunk is missing. The client detects short Content-Length responses and reported transport failures, but cannot prove completion in that ambiguous case. Applications requiring confirmation should inspect the expected image/digest after completion.

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#images.progress`.
