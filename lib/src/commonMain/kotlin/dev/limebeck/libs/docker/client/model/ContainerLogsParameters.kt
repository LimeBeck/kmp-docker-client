package dev.limebeck.libs.docker.client.model

/**
 * Selects output and history for [dev.limebeck.libs.docker.client.api.Containers.getLogs].
 * The default reads available stdout/stderr history and finishes; set [follow] for live output.
 *
 * @property follow Continue waiting for new output after historical logs have been read.
 * @property stdout Include standard output.
 * @property stderr Include standard error. TTY output merges stdout/stderr at the daemon.
 * @property timestamps Ask Docker to prefix each log entry with a timestamp.
 * @property tail Number of lines from the end as a decimal string, or "all"; null omits the
 * parameter and uses Docker's default of all available lines.
 * @property since Earliest log timestamp, in Unix seconds (not milliseconds); null imposes no lower bound.
 * @property until Latest log timestamp, in Unix seconds; null imposes no upper bound.
 * @see dev.limebeck.libs.docker.client.api.Containers.getLogs
 */
data class ContainerLogsParameters(
    val follow: Boolean = false,
    val stdout: Boolean = true,
    val stderr: Boolean = true,
    val timestamps: Boolean = false,
    val tail: String? = null,
    val since: Long? = null,
    val until: Long? = null
)
