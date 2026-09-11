package dev.limebeck.libs.docker.client.model

import io.ktor.http.HttpStatusCode

/** An HTTP error returned when collecting a cold Docker stream. */
class DockerApiException(
    val status: HttpStatusCode,
    val error: ErrorResponse,
) : IllegalStateException("Docker API returned HTTP ${status.value}: ${error.message}")
