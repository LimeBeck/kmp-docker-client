# Container lifecycle and persistent data

Available since the published `1.0.0-rc` and included in the upcoming stable `1.0.0`. Existing convenience overloads remain available.

The complete `ContainerCreateRequest` overload supports host configuration and network attachment. The portable `ContainerConfig` overload remains available.

```kotlin
val volume = client.volumes.create(VolumeCreateOptions(name = "app-data")).getOrThrow()
val network = client.networks.create(NetworkCreateRequest(name = "app-network")).getOrThrow()
val request = ContainerCreateRequest(
    image = "my-app:current",
    env = listOf("APP_MODE=production"),
    hostConfig = HostConfig(
        mounts = listOf(Mount(type = Mount.Type.VOLUME, source = volume.name, target = "/data")),
        portBindings = mapOf("8080/tcp" to listOf(PortBinding(hostIp = "127.0.0.1", hostPort = "8080"))),
    ),
    networkingConfig = NetworkingConfig(
        endpointsConfig = mapOf("app-network" to EndpointSettings(aliases = listOf("app"))),
    ),
)
val id = client.containers.create(name = "app", config = request).getOrThrow().id
client.containers.start(id).getOrThrow()
```

Use the `api.*` and `model.*` imports. The image must already exist on the daemon (pull it first when needed). A successful create is not a readiness check: start separately, inspect state/health and verify application availability.

For replacement, the application should keep its desired request and inspect the current container, including its actual mounts and network endpoints. Pull and validate the replacement image before stopping the current container. Stop the old instance, keep its ID and data, and rename it when reusing the service name. Create the replacement with an explicit configuration and the same named volume. Start it and check readiness before deleting the old instance. If creation/start/readiness fails, remove only the failed replacement, restore the old name and restart the old container. Serialize changes to each managed service and track IDs even when a request outcome is uncertain; reconcile with inspect/list before retrying.

Changing environment variables, ports or mounts requires recreation. `containers.update` changes supported resource limits and restart policy; it does not replace the container configuration. Do not blindly turn an inspect response into a create request: it includes runtime-only fields.

`containers.remove(id)` uses `v=false`. A named volume remains after deleting either container. Delete it separately with `volumes.remove(name)` only when the application explicitly requests data deletion. Avoid pruning as part of replacement. Anonymous volumes need their actual daemon-assigned names retained and remounted explicitly. Bind-mounted data belongs to the host filesystem. Data persistence does not provide backups or undo database migrations; rollback after a schema migration needs an application-specific policy.

`ContainerRecreateTest` validates the SDK sequence against real Docker on JVM, Node.js and Linux X64, including conflicting names, a missing image, environment changes, ports/network/mount inspection, restart-policy updates, and retained data after removal. The test cleans up only resources it creates. End-to-end readiness and rollback in a production dashboard remain application acceptance checks.

Contract: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#containers.recreate`.
