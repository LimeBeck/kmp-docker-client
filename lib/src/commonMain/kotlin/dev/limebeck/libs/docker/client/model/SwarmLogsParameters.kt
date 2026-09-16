package dev.limebeck.libs.docker.client.model

/**
 * Service/task log options. History is read once by default; follow keeps the request open.
 * since is Unix seconds; tail is a decimal line count or "all" (null uses Docker's default).
 * details includes context supplied by Docker. TTY merges stdout and stderr.
 */
data class SwarmLogsParameters(
    val follow: Boolean = false,
    val stdout: Boolean = true,
    val stderr: Boolean = true,
    val timestamps: Boolean = false,
    val details: Boolean = false,
    val since: Long? = null,
    val tail: String? = null,
)
