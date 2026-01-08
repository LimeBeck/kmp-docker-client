# KMP Docker Client

[![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/ktor-3.3.3-orange.svg?logo=ktor)](https://ktor.io)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

A Docker Remote API client library written in Kotlin Multiplatform using Ktor.

## 🚀 Features

- **Kotlin Multiplatform**: Supports JVM, Linux X64, and NodeJS.
- **Ktor Client**: Leverages the power of Ktor for network requests, providing flexible engine support.
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
    implementation("dev.limebeck.libs:docker-client:0.0.1")
}
```

## 📖 Usage

### Initializing the Client

By default, the client is configured to connect via the local Unix socket:

```kotlin
val dockerClient = DockerClient()
```

Custom configuration using `DockerClientConfig`:

```kotlin
val config = DockerClientConfig(
    json = Json { ignoreUnknownKeys = true },
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
        return client.get("/my/custom/endpoint").parse()
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

## 🛠 Development

The project uses OpenAPI Generator to create data models. When building the project, models are automatically generated into the `lib/build/generated/openapi` directory.

To build the project:
```bash
./gradlew build
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📜 License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.
