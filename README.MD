# Ktor Docker Client

A Docker Remote API client library written in Kotlin Multiplatform using Ktor.

## Features

- **Kotlin Multiplatform**: Supports JVM and Linux X64.
- **Ktor Client**: Leverages the power of Ktor for network requests.
- **Type Safety**: Data models are generated from the official Docker OpenAPI specification (v1.51).
- **Coroutines & Flow**: Asynchronous operations and streaming support (logs, stats) via Kotlin Coroutines and Flow.
- **Unix Domain Sockets**: Support for connecting via `/var/run/docker.sock`.

## Current Limitations

- Currently, supports only Linux (due to Unix socket dependency).
- Only Unix sockets are supported for now.
- No authentication support yet.

## Supported Platforms

- JVM
- Linux X64
- NodeJS

## Usage

### Adding Dependency

You can publish the library locally for testing:
```bash
./gradlew :shared:publishToMavenLocal
```

### Initializing the Client

By default, the client is configured to connect via the local Unix socket:

```kotlin
val dockerClient = DockerClient()
```

You can customize the configuration using `DockerClientConfig`:

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

### Extending the API

If you need to use an endpoint that is not yet implemented in the library, you can easily extend it by following the pattern used in the library.

For example, to add support for a custom endpoint:

```kotlin
private object MyCustomApiKey

val DockerClient.myCustomApi: MyCustomApi
    get() = (this as ApiCacheHolder).apiCache.getOrPut(MyCustomApiKey) {
        MyCustomApi(this)
    } as MyCustomApi

class MyCustomApi(val dockerClient: DockerClient) {
    suspend fun getCustomInfo(): Result<MyCustomResponse, ErrorResponse> = with(dockerClient) {
        return client.get("/my/custom/endpoint").parse()
    }
}
```

## Project Structure

- `shared`: The main module containing the Docker client implementation and generated models.
- `specs`: Contains the Docker API OpenAPI specification.
- `sample/terminalApp`: A sample console application using the library.

### Running the Sample

To run the sample on Linux:
```bash
./gradlew :sample:terminalApp:runDebugExecutableLinuxX64
```

## Development

The project uses OpenAPI Generator to create data models. When building the project, models are automatically generated into the `shared/build/generated/openapi` directory.

To build the project, use:
```bash
./gradlew build
```
