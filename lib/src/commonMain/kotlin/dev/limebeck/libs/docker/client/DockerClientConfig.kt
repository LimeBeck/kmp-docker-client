package dev.limebeck.libs.docker.client

import kotlinx.serialization.json.Json

/**
 * Settings shared by a Docker client and its API groups.
 *
 * @property json JSON codec; defaults tolerate unknown fields and omitted explicit nulls.
 * @property connectionConfig Explicit Docker endpoint; defaults to /var/run/docker.sock.
 * @property auth Mutable registry-address-to-credentials map used by image pull/push. Kept in memory only;
 * callers must coordinate concurrent mutations. Docker CLI credential helpers are not read.
 */
data class DockerClientConfig(
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    },
    val connectionConfig: ConnectionConfig = ConnectionConfig.SocketConnection("/var/run/docker.sock"),
    val auth: MutableMap<String, Auth> = mutableMapOf(),
) {
    /**
     * Supported daemon transports; Docker contexts and DOCKER_HOST are not resolved automatically.
     */
    sealed interface ConnectionConfig {
        /**
         * Unix socket endpoint.
         * @param socketPath Filesystem path accessible to the calling process.
         */
        data class SocketConnection(val socketPath: String) : ConnectionConfig
    }

    /**
     * In-memory registry authentication, separate from permission to access the Docker daemon.
     */
    sealed interface Auth {
        /**
         * Registry username/password pair. Its string representation masks credentials.
         * @property username Registry account name.
         * @property password Registry password or access token used as a password.
         */
        data class Credentials(val username: String, val password: String) : Auth {
            override fun toString(): String =
                "Credentials(username=${username.take(3)}***, password=***)"
        }

        /**
         * Registry identity token returned by Docker authentication.
         * @property token Secret identity token; excluded from the string representation.
         */
        data class Token(val token: String) : Auth {
            override fun toString(): String = "Token(token=***)"
        }
    }
}