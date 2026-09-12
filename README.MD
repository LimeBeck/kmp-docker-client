# KMP Docker Client

[![Maven Central Version](https://img.shields.io/maven-central/v/dev.limebeck.libs/docker-client)](https://central.sonatype.com/artifact/dev.limebeck.libs/docker-client)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/ktor-3.5.2-orange.svg?logo=ktor)](https://ktor.io)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

A Docker Remote API client library written in Kotlin Multiplatform using Ktor.

Requires a Docker daemon supporting API 1.51 (Docker 28.3 or later with API 1.51 enabled). API version negotiation is not implemented.

## 🚀 Features

- **Kotlin Multiplatform**: Supports JVM, Linux X64, and NodeJS.
- **Ktor Client**: Uses the CIO engine with Unix domain socket transport.
- **Type Safety**: Data models are generated from the official Docker OpenAPI specification (v1.51).
- **Coroutines & Flow**: First-class support for asynchronous operations and streaming (logs, stats) via Kotlin Coroutines and Flow.
- **Unix Domain Sockets**: Built-in support for connecting via `/var/run/docker.sock`.

## 📦 Supported Platforms

- [x] **JVM**
- [x] **Linux X64**
- [x] **NodeJS**

## 🛠 Installation

Add dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.limebeck.libs:docker-client:0.1.0")
}
```

## API Implementation Status

### Implemented

- **Container**: Manage containers (create, list, start, stop, etc.).
- **Image**: Manage images (pull, list, delete, etc.).
- **Network**: Manage networks (create, list, connect, etc.).
- **Volume**: Manage volumes (create, list, delete, etc.).
- **Exec**: Run commands inside containers.
- **System**: System-level operations (ping, version, auth, etc.).

### Not Implemented

- **Swarm**
- **Node**
- **Service**
- **Task**
- **Secret**
- **Config**
- **Plugin**

## 📖 Usage

For the upcoming release candidate, see [container recreation and persistent data](docs/CONTAINER-LIFECYCLE.md).
Long operations support [image progress callbacks and cancellation](docs/IMAGE-PROGRESS.md).

### Initializing the Client

By default, the client is configured to connect via the local Unix socket:

```kotlin
val dockerClient = DockerClient()
```

Custom configuration using `DockerClientConfig`:

```kotlin
val config = DockerClientConfig(
    json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    },
    connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection("/var/run/docker.sock"),
)
val dockerClient = DockerClient(config)
```

### Working with Containers

The library provides a DSL-like access to various parts of the Docker API.

#### Listing Containers

```kotlin
val containers = dockerClient.containers.getList().getOrNull()
containers?.forEach { println(it.names) }
```

#### Creating and Starting a Container

```kotlin
val createdContainer = dockerClient.containers.create(
    name = "my-container",
    config = ContainerConfig(
        image = "bash",
        cmd = listOf("-c", "while true; do date; sleep 2; done")
    )
).getOrThrow()

dockerClient.containers.start(createdContainer.id).getOrThrow()
```

#### Streaming Logs

```kotlin
val logsFlow = dockerClient.containers.getLogs(
    id = createdContainer.id,
    parameters = ContainerLogsParameters(
        follow = true,
        stdout = true,
        stderr = true
    )
).getOrThrow()

logsFlow.collect { logLine ->
    println("${logLine.type}: ${logLine.line}")
}
```

### 🧩 Extending the API

If you need to use an endpoint that is not yet implemented, you can easily extend the client:

```kotlin
val DockerClient.myCustomApi by ::MyCustom.api()

class MyCustom(val dockerClient: DockerClient) {
    suspend fun getCustomInfo(): Result<MyCustomResponse, ErrorResponse> = with(dockerClient) {
        return client.get(apiPath("/my/custom/endpoint")).parse()
    }
}
```

## ⚠️ Current Limitations

- Primarily tested on Linux (due to Unix socket dependency).
- Only Unix sockets are supported for now.

## 📂 Project Structure

- `lib`: The main module containing the Docker client implementation and generated models.
- `specs`: Contains the Docker API OpenAPI specification.
- `sample/terminalApp`: A sample console application using the library.
- `sample/htmxDashboard`: A sample web dashboard using Ktor and HTMX to monitor containers.

### Running the Samples

Terminal App:
```bash
./gradlew :sample:terminalApp:runDebugExecutableLinuxX64
```

HTMX Dashboard:
```bash
./gradlew :sample:htmxDashboard:runDebugExecutableLinuxX64
```

Terminal and Exec panels automatically fit their available space and update the Docker TTY dimensions. Use **Full screen** to expand the terminal and **Exit full screen** or **Esc** to return. If browser fullscreen is unavailable, the panel expands within the tab.

The dashboard listens on `127.0.0.1:8080`. It is a local development example;
remote access requires a separate authenticated HTTP/WebSocket design.

HTTP diagnostics log headers only, with authorization and registry credentials masked.
Auth endpoint exchanges and request/response bodies are excluded from HTTP logging.

Logs, streaming stats, and events open their HTTP connection when the Flow is collected.
A preparation `Result` containing a Flow does not guarantee that the subsequent HTTP request succeeds.
Non-success HTTP responses during collection throw `DockerApiException`, which exposes `status`
and the decoded `error: ErrorResponse`. Connections close on completion or cancellation;
exceptions thrown by event collectors propagate to the caller.

## 🛠 Development

Use JDK 21 to run the Gradle wrapper. The library targets JVM 17, Linux X64, and NodeJS.
Dependency versions are maintained in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
After changing Kotlin/JS dependencies, run `./gradlew kotlinUpgradeYarnLock` and include the updated
`kotlin-js-store/yarn.lock` in the change.

The project uses OpenAPI Generator to create data models. When building the project, models are automatically generated into the `lib/build/generated/openapi` directory.

To build the project:
```bash
./gradlew build
```

The test suite includes Docker integration tests. They require a running Docker daemon accessible through
`/var/run/docker.sock` and may pull images and create temporary Docker resources. Run them against
a development daemon. Native and JS tasks also download their compiler/runtime dependencies.

Run the standalone image reference parser tests without Docker:

```bash
./gradlew :lib:jvmTest --tests '*OciImageRefParserTest'
```

Run HTTP regression tests against an in-process Unix socket mock, without Docker:

```bash
./gradlew :lib:jvmTest --tests '*DockerHttpRegressionTest'
```

Run all library tests (JVM, Linux X64, and NodeJS):

```bash
./gradlew :lib:allTests
```

Generate API documentation locally:

```bash
./gradlew :lib:dokkaGenerateHtml
```

The HTML output is in `lib/build/dokka/html`. The docs workflow deploys it to GitHub Pages on pushes
to `master`. Release tags (`v<semver>`) trigger build, test, and Maven Central publication; the tag
supplies the published library version. Local builds target the upcoming `1.0.0-rc` release unless `-PlibVersion=...` is supplied.
The dependency example above uses the latest published version, `0.1.0`.

Project contracts and the continuation log are in [`specs/ipc`](specs/ipc/README.md) and [`WAL.md`](WAL.md).

## Interactive terminal output

Use `incomingChunks` for exec/attach terminals. It delivers bytes immediately, including prompts without a newline, preserves CR/ANSI sequences, and removes Docker multiplex headers for non-TTY sessions.

```kotlin
client.exec.startInteractive(execId).getOrThrow().use { session ->
    session.incomingChunks.collect { chunk ->
        terminal.write(chunk.bytes) // Feed binary data to your terminal or a streaming UTF-8 decoder.
    }
}
```

Run input forwarding concurrently using `session.send(bytes)` or `session.send(text)`. For an exec created with `tty = false`, also pass `tty = false` to `startInteractive`. `chunk.type` distinguishes stdout/stderr for non-TTY output. Chunk boundaries can split UTF-8 characters; decoding each chunk independently can corrupt text.

Collect exactly one of `incomingChunks` and the compatibility `incoming` flow, once per session. Collection completion, cancellation (including `first()`/`take()`), or failure closes the connection. Close the session explicitly if you never collect output. `close()` is idempotent; sending after close fails. Avoid reading `session.connection.read` directly: this bypasses buffered handshake bytes, framing, and collection cleanup. The older `incoming` API still buffers TTY text until a line ending.

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#exec.streams`.

Migration from 0.0.10: `incoming.first()` now closes the session, and output cannot be collected again. Use one long-lived collector to process multiple messages. The unscoped `prependLeftover` utility has been removed; use the session output API, which owns forwarding and cleanup.

## Logs, stats and events: lifetime and recovery

Stream requests open on collection and close on EOF, failure, or cancellation (`first()`/`take()` included). Stats with `stream = false` remain an ordinary one-shot request. `getLogs` inspects the container during preparation to determine TTY mode; the log request itself opens on collection. Output processing applies backpressure. There is no fixed request-duration or idle timeout for these streams; use coroutine cancellation or `withTimeout` when the application needs a deadline.

TTY lines and JSON stream records are limited to 1,048,576 characters, and multiplex log frames to 1,048,576 bytes. Oversized records and invalid/truncated frames fail instead of allocating unbounded memory. Stats fail on malformed JSON; events skip malformed JSON records. CR, LF, CRLF and complete final records without a newline are supported. HTTP failures throw `DockerApiException`; transport and decoding errors propagate to the collector.

Docker unsigned counters now use `ULong`/`UInt` (a model API change in 0.1.0); CPU nanoseconds and byte counters retain their full unsigned range.

CIO can report a disconnect between complete HTTP chunks as normal EOF, even when the terminal zero chunk is missing. Handle both EOF and exceptions when recovering a live subscription. Truncation within a chunk or a declared Content-Length body is reported as an error.

The SDK never reconnects implicitly. Recollect a flow to open a new stream after clean EOF or failure. After a daemon restart, use a bounded exponential delay with jitter at the application level, and retry only transport failures or selected server errors. Do not retry cancellation, consumer failures, invalid records, or permanent HTTP errors. Keep the transport retry boundary upstream of application processing.

For events, create a new `events(since = savedTimestamp, filters = ...)` subscription with a small overlap and deduplicate replayed events. Refresh the container/image/network/volume snapshot after reconnect: event history is finite, so a timestamp alone cannot guarantee gap-free recovery. For logs, choose `since`/`tail` explicitly to control replay; prepare `getLogs` again if the container may have been recreated. Stats can simply subscribe again and treat the first CPU sample as a fresh baseline.

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.streams.recovery`.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📜 License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.
