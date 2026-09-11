package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.DockerClientConfig
import dev.limebeck.libs.docker.client.model.AuthConfig
import dev.limebeck.libs.docker.client.model.ErrorResponse
import dev.limebeck.libs.docker.client.model.Result
import dev.limebeck.libs.docker.client.model.SystemAuthResponse
import io.ktor.client.request.*
import io.ktor.http.*

const val DEFAULT_SERVER_ADDRESS = "https://index.docker.io/v1/"
const val AUTH_HEADER = "X-Registry-Auth"

/**
 * Check auth configuration
 *
 * Validate credentials for a registry and, if available, get an identity token for accessing the registry without password.
 */
suspend fun DockerClient.auth(authConfig: AuthConfig): Result<SystemAuthResponse, ErrorResponse> {
    return client.post(apiPath("/auth")) {
        contentType(ContentType.Application.Json)
        setBody(authConfig)
    }.parse<SystemAuthResponse>().onSuccess {
        config.auth[authConfig.serveraddress ?: DEFAULT_SERVER_ADDRESS] = when (it.identityToken) {
            null -> DockerClientConfig.Auth.Credentials(authConfig.username, authConfig.password)
            else -> DockerClientConfig.Auth.Token(it.identityToken)
        }
    }
}

/**
 * Resolve server address for registry.
 *
 * @param registry Registry name (e.g., "docker.io")
 * @return List of server address candidates in priority order
 */
fun resolveServerForRegistry(registry: String): List<String> {
    // return candidates in priority order
    return if (registry == "docker.io") {
        listOf(
            "https://index.docker.io/v1/",
            "https://index.docker.io/v1",
            "https://registry-1.docker.io",
            "registry-1.docker.io",
            "docker.io"
        )
    } else {
        // for non-hub registries people may store both forms
        listOf(
            "https://$registry/",
            "https://$registry",
            registry
        )
    }
}
