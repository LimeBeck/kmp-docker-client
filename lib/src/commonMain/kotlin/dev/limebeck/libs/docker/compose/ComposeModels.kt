package dev.limebeck.libs.docker.compose

import dev.limebeck.libs.docker.client.model.ContainerState
import dev.limebeck.libs.docker.client.model.LogLine

/** Container-backed discovery snapshot. No desired configuration or readiness is inferred. */
data class ComposeDiscovery(
    val projects: List<ComposeProject>,
    /** Compose-labeled containers whose project label is absent or blank. */
    val unassignedContainers: List<ComposeContainer>,
)

/** Observed services plus containers whose service label is absent or blank. */
data class ComposeProject(
    val name: String,
    val services: List<ComposeService>,
    val unassignedContainers: List<ComposeContainer>,
)

/** Observed containers, including stopped replicas, one-offs and unknown one-off metadata. */
data class ComposeService(val name: String, val containers: List<ComposeContainer>) {
    /** Observed regular replicas; this is not the desired scale from compose.yaml. */
    val replicas: List<ComposeContainer> get() = containers.filter { it.oneOff == false }
}

/**
 * Metadata from labels and inspect. Nullable metadata remains unknown, never guessed from names.
 * [state] retains Docker health separately from running status; running does not imply healthy.
 * Labels and daemon data are untrusted and may contain sensitive information.
 */
data class ComposeContainer(
    val id: String,
    val name: String?,
    val project: String?,
    val service: String?,
    val replicaNumber: Int?,
    val oneOff: Boolean?,
    val image: String?,
    val state: ContainerState?,
)

/** One SDK log record with its source. TTY records retain the SDK's UNKNOWN stream type. */
data class ComposeLogLine(
    val project: String,
    val service: String?,
    val containerId: String,
    val containerName: String?,
    val replicaNumber: Int?,
    val oneOff: Boolean?,
    val log: LogLine,
)
