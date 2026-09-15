# Module Kotlin Multiplatform Client for Docker

## User guide

KMP Docker Client provides coroutine-based access to a single Docker host. Start here for complete workflows, then use the package and class navigation below for individual API methods.

This guide covers the upcoming **1.0.1**, including `DockerClient.use`. The published Maven Central baseline is **1.0.0**; the lifecycle additions require a 1.0.1 development build until release. Published targets are JVM, Kotlin/JS on Node.js and Linux X64 Native, tested on Linux with Docker 28.5.2/29.0.0 and API 1.51. JVM bytecode targets Java 17. macOS/Windows support is planned separately.

### Install

Use Maven Central. In a Kotlin Multiplatform project, add the dependencies to `commonMain.dependencies`; in a JVM project, use the ordinary `dependencies` block:

```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("dev.limebeck.libs:docker-client:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-io:3.5.2")
}
```

Use Kotlin 2.4.20 and align any explicit Ktor dependencies with 3.5.2. Other compiler/dependency combinations are not part of the tested matrix. Do not select browser JS: the JS transport requires Node.js.

### Connect and list containers

The process needs access to the Docker Unix socket. `/var/run/docker.sock` is the default; pass the actual socket path for a rootless or custom daemon. Docker contexts and `DOCKER_HOST` are not discovered automatically. TCP/TLS and Windows named pipes are not supported in this release. API 1.51 is fixed; no negotiation is performed.

All following examples use these imports. The examples are suspending functions to call from your application's coroutine scope; a JVM command-line program can call them inside `runBlocking`.

<!-- compile-sample -->
```kotlin
import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.DockerClientConfig
import dev.limebeck.libs.docker.client.api.*
import dev.limebeck.libs.docker.client.diagnostics.*
import dev.limebeck.libs.docker.client.model.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

suspend fun listContainers(socketPath: String = "/var/run/docker.sock") {
    DockerClient(DockerClientConfig(
        connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(socketPath),
    )).use { docker ->
        docker.system.ping().getOrThrow()
        docker.containers.getList().getOrThrow().forEach { println(it.names) }
    }
}
```

`DockerClient.use` requires the upcoming 1.0.1 build. On published 1.0.0/1.0.0-rc, retain `try/finally` with `docker.client.close()`.

Reuse a client for an application lifecycle. Stop and join your collectors and close interactive sessions before calling `docker.close()` at shutdown. `DockerClient` implements `AutoCloseable`; raw terminal sessions have their own lifetime and must be closed separately. Examples below receive an application-owned `docker` client.

API entry points: [DockerClient][dev.limebeck.libs.docker.client.DockerClient], [Containers][dev.limebeck.libs.docker.client.api.Containers], [Images][dev.limebeck.libs.docker.client.api.Images], [Volumes][dev.limebeck.libs.docker.client.api.Volumes], [Networks][dev.limebeck.libs.docker.client.api.Networks], [Exec][dev.limebeck.libs.docker.client.api.Exec] and [System][dev.limebeck.libs.docker.client.api.System]. Import `api.*` to bring the `containers`, `images`, `volumes`, `networks`, `exec` and `system` extension properties into scope.

### Results and exceptions

Ordinary operations return this library's `Result<T, ErrorResponse>`, not Kotlin's single-parameter `Result<T>`. Use `fold`, `onError` or `errorOrNull` to handle daemon errors. `getOrThrow()` is convenient when any error should abort the current workflow; SDK errors with operation context throw `DockerResultException` (an `IllegalStateException`), retaining the original error and available cause. Read `failure.dockerContext` for sanitized operation details; raw errors and causes may contain sensitive data. Caller-created error results without context throw a plain `IllegalStateException`.

<!-- compile-sample -->
```kotlin
suspend fun inspectContainer(docker: DockerClient, id: String) {
    docker.containers.getInfo(id).fold(
        onSuccess = { container -> println(container.state) },
        onError = { failure -> println("Docker rejected inspect: ${failure.message}") },
    )
}
```

Transport and decoding exceptions can also escape ordinary calls. Cancellation always propagates. A `Result` containing a Flow only describes preparation: HTTP stream errors occur during collection as `DockerApiException`, with `status` and `error` properties. Treat callback/collector exceptions as application failures, not retryable Docker errors.

### Pull an image, then create and start

Pull first: container creation does not implicitly download a missing image. Here `name` must be a new container name, and the function returns its ID for later management. The callback is suspending and runs sequentially, applying backpressure. The returned Result after the progress stream completes is the final outcome; a completed layer or HTTP 200 alone is not success.

<!-- compile-sample -->
```kotlin
suspend fun createWorker(docker: DockerClient, name: String): String {
    withTimeout(120_000) {
        docker.images.create("alpine:latest") { progress ->
            println("${progress.id.orEmpty()}: ${progress.status.orEmpty()}")
        }.getOrThrow()
    }
    val id = docker.containers.create(name = name, config = ContainerCreateRequest(
        image = "alpine:latest",
        cmd = listOf("sh", "-c", "while true; do date; sleep 5; done"),
        env = listOf("APP_MODE=development"),
    )).getOrThrow().id
    docker.containers.start(id).getOrThrow()
    return id
}
```

If start fails, the created container still exists. Keep/reconcile its ID and decide whether to retry or remove it. Running state does not prove application readiness.

### Volumes, networks and published ports

`ContainerCreateRequest` includes host and networking configuration; the older `ContainerConfig` overload is available for simpler creation. This example expects the image to be present and an application inside it to serve port 8080. Names and host ports must be available.

<!-- compile-sample -->
```kotlin
suspend fun createService(docker: DockerClient, name: String, image: String): String {
    val volume = docker.volumes.create(VolumeCreateOptions(name = "$name-data")).getOrThrow()
    val networkName = "$name-network"
    docker.networks.create(NetworkCreateRequest(name = networkName)).getOrThrow()
    return docker.containers.create(name = name, config = ContainerCreateRequest(
        image = image,
        hostConfig = HostConfig(
            mounts = listOf(Mount(type = Mount.Type.VOLUME, source = volume.name, target = "/data")),
            portBindings = mapOf("8080/tcp" to listOf(
                PortBinding(hostIp = "127.0.0.1", hostPort = "8080"),
            )),
        ),
        networkingConfig = NetworkingConfig(endpointsConfig = mapOf(
            networkName to EndpointSettings(aliases = listOf("app")),
        )),
    )).getOrThrow().id
}

suspend fun removeContainerKeepData(docker: DockerClient, id: String) {
    docker.containers.stop(id, t = 10).getOrThrow()
    docker.containers.remove(id, v = false).getOrThrow()
}
```

Provisioning is not transactional: if a later call fails, earlier resources remain. Track resource IDs/names you created and reconcile before cleanup; never delete an existing volume just because container creation failed. Named volumes survive container removal. Only an explicit data-deletion action should call `volumes.remove(name)`. Remove an unused network separately with `networks.remove(id)`.

Environment, ports and mounts require container recreation. Keep your desired create request, prepare the new image, stop/retain the old container, start the replacement with the same named volume, check readiness and only then remove the old instance. `containers.update` changes supported resource limits/restart policy, not arbitrary configuration. A rollback cannot undo writes to shared volumes or database migrations. See the [detailed lifecycle guide](https://github.com/LimeBeck/kmp-docker-client/blob/master/docs/CONTAINER-LIFECYCLE.md).

### Logs, stats and events

Follow mode can run indefinitely. Own it with a coroutine job or deadline. This example stops all three subscriptions after 30 seconds and waits for cleanup. It preserves transport/application failures and shows the HTTP error boundary explicitly.

<!-- compile-sample -->
```kotlin
suspend fun observeForThirtySeconds(docker: DockerClient, id: String) {
    try {
        withTimeoutOrNull(30_000) {
            coroutineScope {
                launch {
                    docker.containers.getLogs(id, ContainerLogsParameters(
                        follow = true, stdout = true, stderr = true,
                    )).getOrThrow().collect { println("${it.type}: ${it.line}") }
                }
                launch {
                    docker.containers.getStats(id).getOrThrow().collect {
                        println(it.memoryStats)
                    }
                }
                launch {
                    docker.system.events(filters = mapOf("container" to listOf(id)))
                        .collect { println(it) }
                }
            }
        }
    } catch (failure: DockerApiException) {
        println("HTTP ${failure.status.value}: ${failure.error.message}")
        throw failure
    }
}

suspend fun oneStatsSample(docker: DockerClient, id: String) {
    val sample = docker.containers.getStats(id, stream = false, oneShot = true)
        .getOrThrow().first()
    println(sample.memoryStats)
}
```

Logs inspect the container during preparation to determine TTY mode; their streaming request opens on collection. Stats with `stream=false` perform the HTTP request immediately and wrap the response in a one-element Flow. Other live requests open when collected and close on EOF, error or cancellation, including `first()`/`take()`. Collection applies backpressure; bound any queues/history you add.

JSON/TTY line records are limited to 1,048,576 characters and multiplex log frames to 1,048,576 bytes. Invalid stats JSON fails; malformed event JSON is skipped. Partial/oversized records fail where framing permits detection. Docker counters use `UInt`/`ULong`; preserve unsigned precision and compute CPU percentages from successive samples, not a single cumulative counter.

### Interactive exec and attach

Create an exec instance in a running container and start it with matching TTY mode. Feed bytes to a terminal or incremental UTF-8 decoder: chunk boundaries can split characters. The input Flow below comes from your application's terminal input. When input ends, this example closes the session; decide separately whether your application should leave a remote shell running.

<!-- compile-sample -->
```kotlin
suspend fun openShell(
    docker: DockerClient,
    containerId: String,
    input: Flow<ByteArray>,
    output: suspend (ByteArray) -> Unit,
) {
    val execId = docker.containers.execCreate(containerId, ExecConfig(
        cmd = listOf("/bin/sh"), tty = true,
        attachStdin = true, attachStdout = true, attachStderr = true,
    )).getOrThrow().id
    docker.exec.startInteractive(execId, tty = true).getOrThrow().use { session ->
        coroutineScope {
            val sender = launch {
                try { input.collect { session.send(it) } }
                finally { session.close() }
            }
            try {
                docker.exec.resize(execId, h = 24, w = 80).getOrThrow()
                session.incomingChunks.collect { output(it.bytes) }
            } finally {
                sender.cancelAndJoin()
            }
        }
    }
}
```

For non-TTY output use `tty=false` both at creation and start, then inspect `chunk.type` to distinguish stdout/stderr. `containers.attach(id, stream=true, stdin=true, stdout=true, stderr=true)` returns the same session abstraction for the container's existing process; it does not create a new shell. Container TTY resize uses `containers.resize`, while exec TTY resize uses `exec.resize`.

Collect exactly one output Flow once per session. Prefer `incomingChunks` for prompts without a newline and ANSI data. The compatibility `incoming` Flow buffers TTY lines. Completion, cancellation or collector failure closes the session; explicitly close a session if you never collect it. Do not read `session.connection.read` directly because that bypasses framing and buffered handshake data.

### Registry credentials, push and load

Registry authentication is separate from permission to access the Docker socket. Supply credentials from the application, not hard-coded source. `auth` validates them through Docker and stores an identity token or credentials in the client's in-memory auth map. The SDK does not load Docker CLI credential helpers or persist secrets.

<!-- compile-sample -->
```kotlin
suspend fun loginToRegistry(docker: DockerClient, username: String, password: String) {
    docker.auth(AuthConfig(
        username = username, password = password,
        serveraddress = "https://registry.example.com",
    )).getOrThrow()
}

suspend fun pushImage(docker: DockerClient, localImage: String) {
    val repository = "registry.example.com/team/app"
    docker.images.tag(localImage, repo = repository, tag = "latest").getOrThrow()
    docker.images.push(repository, tag = "latest") { progress ->
        progress.aux?.let { println("${it.digest}: ${it.size} bytes") }
    }.getOrThrow()
}

suspend fun loadArchive(docker: DockerClient, archive: ByteReadChannel) {
    docker.images.load(body = archive) { println(it.stream ?: it.status) }.getOrThrow()
}
```

Pull/load callbacks receive `ImageProgress<Unit>`; push receives `ImageProgress<ImagePushResult>`. Auxiliary fields and progress totals may be absent. Layer progress is not an overall percentage. Loading consumes the tar channel once; retries need a fresh source. Callbacks and collection have no implicit duration/idle deadline: apply `withTimeout` or cancel your job. Cancelling closes the request, but daemon/registry work already done is not rolled back. See [progress semantics](https://github.com/LimeBeck/kmp-docker-client/blob/master/docs/IMAGE-PROGRESS.md).

HTTP diagnostics mask credential headers and exclude `/auth` exchanges and bodies. Application logs and custom client changes still need to keep secrets private.

### Disconnection and recovery

The SDK does not retry/reconnect automatically. Live streams can finish with normal EOF as well as an exception; CIO cannot distinguish every disconnect between complete HTTP chunks from a clean end. Treat either as a reason to reconcile if the subscription should still be live. Use bounded backoff with jitter for selected transient failures, and propagate cancellation, decoding failures and consumer errors.

Refresh a snapshot after reconnect. Recreate event subscriptions using a saved timestamp with a small overlap and deduplicate; Docker event history is finite. Choose log `since`/`tail` to control replay, and re-prepare logs after container replacement. Reset your stats baseline. Start a new terminal explicitly without replaying commands. Read [recovery and tested boundaries](https://github.com/LimeBeck/kmp-docker-client/blob/master/docs/STREAM-RECOVERY.md).

### Connection diagnostics (1.0.1, unreleased)

The diagnostic extensions are opt-in and do not change errors returned by ordinary API calls.
`diagnoseConnection` checks the configured socket and fixed API version with a five-second default
HTTP timeout. It reads at most 4 KiB of the response and never creates resources or retries operations.

<!-- compile-sample -->
```kotlin
suspend fun checkDockerConnection(docker: DockerClient) {
    val report = docker.diagnoseConnection(timeoutMillis = 5_000)
    println("${report.problem}: ${report.message}")
    println(report.suggestion)
}
```

Import `dev.limebeck.libs.docker.client.diagnostics.*`. A `NONE` result means the versioned ping
succeeded, not that every operation is authorized. For a transport exception from a call, stream or
terminal, use `docker.diagnoseFailure(failure)` to obtain the same categories. Preserve cancellation;
do not classify application callback exceptions as transport failures. Known OS messages/codes are
recognized best-effort; unknown or localized errors remain `UNKNOWN`.
SDK-produced reports contain only a category, fixed messages and HTTP status: no socket paths,
raw exceptions, headers or response bodies. This also applies to `toString()`. The probe is excluded
from the SDK HTTP logging plugin. Caller-held exceptions and custom logging/plugins remain outside
this guarantee; do not send them to the panel or log them without separate handling. A clean stream EOF has no exception and still needs reconciliation.
These additions require the upcoming 1.0.1 release and are not present in published 1.0.0.

### Exception context (1.0.1, unreleased)

HTTP request, response decoding, stream and SDK-created exec/attach session failures carry
safe operation metadata. The SDK retains the original exception type and cause; context is attached
as a suppressed `DockerContextException`. `dockerContext` also follows cause chains created by
coroutine stack recovery. Caller cancellation is not annotated.

<!-- compile-sample -->
```kotlin
suspend fun inspectWithContext(docker: DockerClient, id: String) {
    try {
        docker.containers.getInfo(id).getOrThrow()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        failure.dockerContext?.let { context ->
            println("${context.method} ${context.route}: ${context.stage}, HTTP ${context.httpStatus}")
        }
        throw failure
    }
}
```

Context contains method, an allowlisted route such as `/containers/{resource}/stats`, API version,
failure stage and HTTP status when known. It contains no socket path, resource IDs, query values,
headers or bodies. Unknown routes become `/{unknown}`. A stage records where an error was observed,
not whether retrying is safe. Original exception messages, causes, suppressed cleanup failures and
`DockerApiException.error` remain sensitive: keep full stack traces within trusted debugging.
`DockerApiException.message` itself includes only the numeric HTTP status.

SDK HTTP error Results and image progress failures preserve context through `map`/`mapError`.
Their `getOrThrow()` throws `DockerResultException`, an `IllegalStateException` with safe context
in its message. Its `error` property retains the typed error value, and `cause` retains the original
exception when available. Caller-created Results and raw channels consumed outside SDK boundaries
may have no operation context. Non-HTTP terminal handshake
failures now throw their original exception with context instead of returning only an error string;
HTTP handshake rejections still return an error Result. Caller-created sessions may have no context.

### Troubleshooting and upgrade

| Symptom | Check |
| --- | --- |
| Socket connection fails | Socket path, daemon availability and process permissions; the client does not follow Docker contexts automatically. |
| API version rejected | Daemon must serve API 1.51; this release does not negotiate an older version. |
| Container create fails with missing image | Pull and wait for the final successful Result before creating. |
| Flow preparation succeeds, collection fails | Catch HTTP/transport errors around collection, not only around getLogs/getStats. |
| Terminal prompt delayed or text corrupted | Use incomingChunks and an incremental UTF-8 decoder; match TTY mode. |
| Container replacement fails | Reconcile IDs and preserve volumes; do not blindly repeat destructive steps. |

The 1.0.0 release preserves the published 1.0.0-rc API. From 0.1.0, `DriverData.data` is nullable and `DockerClient.logger` uses Ktor's logger type. From 0.0.x, also account for unsigned counters and session ownership changes. See [migration](https://github.com/LimeBeck/kmp-docker-client/blob/master/docs/MIGRATION-1.0.0.md) and [compatibility policy](https://github.com/LimeBeck/kmp-docker-client/blob/master/docs/COMPATIBILITY.md). Generated public models are covered by the same compatibility policy as handwritten APIs.

Full Docker API coverage, Compose orchestration and application authentication/roles are not supplied by the SDK. The bundled dashboard is an independent example; its UI behavior is not a library release gate.
