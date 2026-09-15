# Compose discovery and logs

Stage 1 adds the `dev.limebeck.libs.docker.compose` package inside `lib` for JVM, NodeJS and Linux X64.
It uses the existing Engine client and adds no runtime dependencies.
Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#deferred.compose.discovery`.

## Availability

Available in **1.1.0** inside the existing artifact; no separate Compose module is needed.
Release publication is in progress.

```kotlin
implementation("dev.limebeck.libs:docker-client:1.1.0")
```

Use `implementation(project(":lib"))` when working in this repository.
Compose CLI is needed only by the real-Compose test harness.

## Usage

```kotlin
import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.ContainerLogsParameters
import dev.limebeck.libs.docker.compose.compose

suspend fun inspectCompose() {
    DockerClient().use { docker ->
        val snapshot = docker.compose.discover().getOrThrow()
        for (project in snapshot.projects) {
            for (service in project.services) {
                for (container in service.containers) {
                    println("${project.name}/${service.name}: ${container.id} " +
                        "state=${container.state?.status} health=${container.state?.health?.status}")
                }
            }
        }
        val worker = docker.compose.getService("my-project", "worker").getOrThrow()
        println("Observed regular replicas: ${worker?.replicas?.size}")
        docker.compose.logs(
            project = "my-project",
            services = setOf("api", "worker"),
            parameters = ContainerLogsParameters(tail = "100"),
        ).collect { record ->
            println("${record.service}/${record.containerId} ${record.log.type}: ${record.log.line}")
        }
    }
}
```

Use `follow = true` to wait for live logs, with a caller-owned timeout or cancellation scope.
The integration borrows the client, uses its configured Unix socket and never closes it.
Do not close the client until log collectors have stopped.

## Discovery semantics

- Membership comes from [Compose labels](https://docs.docker.com/reference/compose-file/services/#labels),
  never container names. Replica and one-off metadata use the
  [Compose implementation labels](https://github.com/docker/compose/blob/main/pkg/api/labels.go).
- All containers are listed, including stopped ones. Only Compose-labeled candidates are inspected.
  Each candidate costs one inspect request, performed sequentially. Inspection supplies actual health
  rather than parsing the human-readable container status string.
- Projects and services are sorted by name; containers by positive replica number and then ID.
  `replicas` includes only confirmed `oneOff == false` containers, including stopped replicas.
  Missing/invalid one-off or replica labels remain null. Desired scale and readiness are unknown.
- Missing/blank project labels go into `ComposeDiscovery.unassignedContainers`;
  missing/blank service labels go into `ComposeProject.unassignedContainers`.
- Discovery is container-backed. Projects with only networks/volumes, removed projects, services
  scaled to zero and services never created cannot be reconstructed. No Compose file is read or
  inferred from labels. Networks and volumes can still be accessed using the core SDK.
- A running container is not necessarily healthy; state and health remain separate. Snapshots are
  observations across several requests, not atomic daemon snapshots. A container disappearing
  during inspection fails discovery with its SDK error. `getProject`/`getService` return successful
  null for an absent match; they currently perform a full discovery.
- HTTP errors preserve the SDK Result and diagnostic context. Transport, decoding and cancellation
  exceptions propagate. Labels and daemon state/log output are untrusted and may contain secrets.

## Log semantics

Each collection discovers its container set afresh. New replicas are not subscribed automatically.
The `services: Set<String>?` parameter selects exact service names. Null includes all project
containers, even those without service labels; a nonempty set includes all replicas of the named
services. An empty set yields an empty flow without contacting Docker. Blank names are rejected.
The set is copied when logs() is called; later caller mutations do not change the selection.
For one service, use `logs("my-project", service = "worker")`; this overload delegates to
`logs("my-project", services = setOf("worker"))` and accepts the same history and stream options. `includeOneOff = false` includes only confirmed regular replicas; the default includes
one-offs and unknown one-off metadata. A missing project or service yields an empty flow.

Every record carries project, service, container ID/name, replica number, one-off metadata and the
original SDK `LogLine`. Stdout/stderr and per-container ordering are preserved. TTY output retains
the SDK's merged/unknown stream identity. There is no global timestamp sorting or line rewriting.
History options, including `tail`, apply separately to each container.

The default `maxStreams = 64` bounds simultaneous subscriptions. A larger selection fails before
opening any log requests, rather than silently dropping replicas. Output uses rendezvous
backpressure; slow collectors suspend producers. One pending record per producer and the core
SDK's stream buffers still consume memory. Any producer error cancels siblings and fails collection.
Consumer failure, cancellation and completion release requests. There is no automatic reconnect,
resubscription or rollback. Discovery and log preparation errors surface during collection via the
SDK `getOrThrow` behavior; errors during streaming preserve the original SDK exceptions.

## Verification

The Compose unit tests require no daemon:

```shell
./gradlew :lib:jvmTest --tests '*Compose*' :lib:jsNodeTest --tests '*Compose*' :lib:linuxX64Test --tests '*Compose*' :lib:checkKotlinAbi --warning-mode=fail
```

Real-Compose tests use a unique disposable project against `/var/run/docker.sock`, regardless of
the current Docker context. The harness creates two healthy worker replicas, a stopped service,
a one-off container, an unrelated container and a container with incomplete labels. It removes
its containers and network on exit without restarting the daemon or touching existing projects.

```shell
scripts/with-compose-fixture.sh ./gradlew :lib:jvmTest --tests '*Compose*' :lib:jsNodeTest --tests '*Compose*' :lib:linuxX64Test --tests '*Compose*' --warning-mode=fail --console=plain --max-workers=2
```

The harness passes a generated fixture name to opt-in Kotlin integration tests on all three targets.
PR CI runs these against its Docker compatibility matrix. The tests verify discovery, health,
replicas, stopped and one-off output, stream identity and repeated collector cancellation/client reuse.
Unit tests additionally assert producer cleanup after consumer failure and sibling stream failure.

Local verification after adding multi-service selection and the String overload on 2026-09-15 passed with Docker 29.8.0
and Compose 5.5.1: 42 tests (12 unit and 2 integration tests per runtime), no failures or skips.
Dokka generation passed. The updated lib ABI baseline adds Compose APIs without removing
existing declarations. Remote Docker compatibility matrix results are pending.

## Later stages

The dashboard already exposes read-only project/service views and logs. Container start/stop/restart
remain a later stage. File-based up/down/pull/build,
profiles and reconciliation belong to a later optional Compose CLI backend. They are not exposed
by this package yet.
