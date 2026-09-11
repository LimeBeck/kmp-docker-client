# KMP Docker Client

[![Maven Central Version](https://img.shields.io/maven-central/v/dev.limebeck.libs/docker-client)](https://central.sonatype.com/artifact/dev.limebeck.libs/docker-client)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/ktor-3.5.2-orange.svg?logo=ktor)](https://ktor.io)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

A Docker Remote API client library written in Kotlin Multiplatform using Ktor.

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
    implementation("dev.limebeck.libs:docker-client:0.0.9")
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
supplies the published library version. Local builds use `snapshot` unless `-PlibVersion=...` is supplied.

Project contracts and the continuation log are in [`specs/ipc`](specs/ipc/README.md) and [`WAL.md`](WAL.md).

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📜 License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.
