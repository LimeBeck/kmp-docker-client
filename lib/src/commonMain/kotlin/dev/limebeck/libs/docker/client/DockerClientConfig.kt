package dev.limebeck.libs.docker.client

import kotlinx.serialization.json.Json

data class DockerClientConfig(
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    },
    val connectionConfig: ConnectionConfig = ConnectionConfig.SocketConnection("/var/run/docker.sock"),
    val auth: MutableMap<String, Auth> = mutableMapOf(),
) {
    sealed interface ConnectionConfig {
        data class SocketConnection(val socketPath: String) : ConnectionConfig
    }

    sealed interface Auth {
        data class Credentials(val username: String, val password: String) : Auth {
            override fun toString(): String =
                "Credentials(username=${username.take(3)}***, password=***)"
        }

        data class Token(val token: String) : Auth {
            override fun toString(): String = "Token(token=***)"
        }
    }
}