# Swarm APIs

Available in **1.3.0** (`dev.limebeck.libs:docker-client:1.3.0`).

Swarm resource APIs are grouped under `docker.swarm`:

```kotlin
import dev.limebeck.libs.docker.client.api.swarm

val secrets = docker.swarm.secrets.getList().getOrThrow()
val configs = docker.swarm.configs.getList().getOrThrow()
```

Accessing the group sends no requests and never initializes or joins a swarm. It uses the
DockerClient's configured socket and lifetime. Resource operations require a Swarm manager;
a standalone daemon or worker can reject them with an SDK error Result. No Docker CLI is needed.

## Cluster, nodes, services and tasks

All Swarm API 1.51 endpoints are exposed under `docker.swarm`:

| Group | Methods |
| --- | --- |
| `swarm` | `getInfo`, `init`, `join`, `leave`, `update`, `getUnlockKey`, `unlock` |
| `swarm.nodes` | `getList`, `getInfo`, `update`, `remove` |
| `swarm.services` | `getList`, `getInfo`, `create`, `update`, `remove`, `getLogs` |
| `swarm.tasks` | `getList`, `getInfo`, `getLogs` |
| `swarm.secrets`, `swarm.configs` | `getList`, `getInfo`, `create`, `update`, `remove` |

Cluster lifecycle methods affect the daemon connected to this client. `init(SwarmInitRequest(...))`
requires a suitable `listenAddr` (for example `0.0.0.0:2377`) and advertised address.
`join(SwarmJoinRequest(...))` sends manager addresses and a join token to the joining daemon.
Use a separate client connected to each node's socket when managing multiple nodes.
`leave(force = false)` never forces a manager to leave implicitly. `getUnlockKey()` and
`unlock(SwarmUnlockRequest(...))` support manager autolock. Cluster `update` supports explicit
rotation of worker/manager join tokens and manager unlock keys. Treat these credentials and
cluster inspection responses as sensitive.

Node updates replace `NodeSpec`, including labels, role and availability; node removal supports
explicit `force`. Service creation and updates accept `ServiceSpec`, including task templates,
replica/job mode, networks, ports, secrets/configs and update/rollback policies. Docker validates
the spec and performs scheduling. An accepted mutation does not imply readiness or convergence.
The SDK does not wait, retry or coordinate changes across nodes. Tasks are observed through
list/inspect/logs; Docker provides no direct task create/update/delete endpoints.

For every update, inspect first, copy the returned spec and pass its `version.index` (`ULong`).
A stale version returns a Docker error. Service `update` accepts `rollback = "previous"` to
request server-side rollback; pass a complete valid spec even though Docker restores the previous
one. `registryAuth` is a pre-encoded Base64url `X-Registry-Auth` value for service create/update.
When absent on update, `registryAuthFrom` selects `"spec"` or `"previous-spec"`.
Service listing supports `status = true`; inspection supports `insertDefaults = true`.
Filters are Docker's JSON filter map; allowed keys vary by resource.

### Service and task logs

`getLogs(id, SwarmLogsParameters(...))` inspects the resource immediately to determine TTY
framing and returns `Result<Flow<LogLine>, ErrorResponse>`. Every collection opens a new request.
The default reads stdout and stderr history and finishes. Options include `follow`, `details`,
`timestamps`, `since` (Unix seconds) and `tail` (decimal count or `"all"`). Docker must support
service logs for the task's logging driver (for example `json-file` or `journald`).

TTY output merges stdout/stderr; other output preserves stream types. Docker supplies any task
context in log text. The SDK adds no task attribution or global ordering. A service update between
inspection and collection can change framing; obtain a new flow after changing TTY settings.
Inspection HTTP failures return Result errors. Collection HTTP failures throw `DockerApiException`;
transport/decoding and consumer failures propagate. Cancellation or completion closes the stream.
Follow has no implicit deadline; use a caller-owned timeout or cancel collection.

## Secrets and configs

Both `secrets` and `configs` provide:

| Method | Behavior |
| --- | --- |
| `getList(filters)` | List objects; optional JSON filters: id, name, names and label. |
| `getInfo(id)` | Inspect an object by ID or name. |
| `create(spec)` | Send SecretSpec/ConfigSpec; return SwarmCreateResponse with the new ID. |
| `update(id, version, spec)` | Update labels using the inspected object's version index. |
| `remove(id)` | Delete an object; Docker rejects objects still used by services. |

`version` is a `ULong`, matching `ObjectVersion.index`. Copy the complete inspected spec and
change only labels. The version is required to prevent overwriting a concurrent update. Stale
versions and attempts to change immutable fields are Docker errors; the SDK does not retry,
merge labels or replace objects automatically. To rotate payload data, create a new object
and update its consumers through the service spec separately.

## Data and errors

Pass Base64-encoded data in `SecretSpec.data` or `ConfigSpec.data`. For example,
`kotlin.io.encoding.Base64.Default.encode(bytes)` encodes a byte array. The SDK serializes
this string unchanged; Docker validates its content and size. Base64 is not encryption.
Secret inspect/list responses omit secret payloads; config responses can contain their data.
Config storage is not a substitute for Docker secrets.

HTTP failures preserve `Result<..., ErrorResponse>`, including operation context. Transport,
decoding and cancellation exceptions propagate. Failure context uses resource route templates,
not actual object names, data or IDs. Raw specs, config responses, error messages and caller-enabled
body logging may contain sensitive content. Close the client only after requests complete.

## Verification

Unix HTTP regression tests check request methods, JSON bodies, filter/path encoding, unsigned
versions, HTTP errors and cancellation:

```sh
./gradlew :lib:jvmTest --tests '*Swarm*HttpTest' --warning-mode=fail
```

Real lifecycle tests create isolated Docker-in-Docker manager and worker daemons and remove them
afterward. JVM, NodeJS and Linux X64 checks cover cluster init/leave/update, token rotation,
node updates, worker join/leave/removal, service creation/update/rollback/removal, task discovery,
TTY and multiplexed logs, secrets/configs and stale versions. A JVM fixture test also restarts the
isolated manager and unlocks it. They do not change host Swarm membership or user resources.

```sh
bash scripts/with-swarm-fixture.sh ./gradlew :lib:jvmTest --tests '*Swarm*' :lib:jsNodeTest --tests '*Swarm*' :lib:linuxX64Test --tests '*Swarm*' --warning-mode=fail --console=plain --max-workers=2
```

The harness requires Docker with permission to run privileged disposable containers. PR CI
runs the fixture with Docker 28.5.2 and 29.0.0; Release CI also runs it.
