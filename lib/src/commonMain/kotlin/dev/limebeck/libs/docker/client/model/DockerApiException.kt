package dev.limebeck.libs.docker.client.model

import io.ktor.http.HttpStatusCode

/**
 * An HTTP error returned when collecting a cold Docker stream.
 * [error] retains raw daemon details and may contain sensitive data; the exception message includes only status.
 * SDK stream failures also expose safe operation metadata via the diagnostics dockerContext extension.
 */
class DockerApiException(
    val status: HttpStatusCode,
    val error: ErrorResponse,
) : IllegalStateException("Docker API returned HTTP ${status.value}")
