package dev.limebeck.docker.client.model

data class ContainerLogsParameters(
    val follow: Boolean = false,
    val stdout: Boolean = true,
    val stderr: Boolean = true,
    val timestamps: Boolean = false,
    val tail: String? = null,
    val since: Long? = null,
    val until: Long? = null
)
