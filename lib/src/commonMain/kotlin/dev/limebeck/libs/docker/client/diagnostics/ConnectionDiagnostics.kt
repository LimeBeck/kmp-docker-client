package dev.limebeck.libs.docker.client.diagnostics

import dev.limebeck.libs.docker.client.DockerClient
import io.ktor.util.AttributeKey
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

internal val DiagnosticProbe = AttributeKey<Unit>("DockerDiagnosticProbe")

/** Outcome of a connection probe or classification of a transport failure. */
enum class ConnectionProblem {
    NONE, SOCKET_NOT_FOUND, PERMISSION_DENIED, CONNECTION_REFUSED, CONNECTION_LOST,
    TIMEOUT, API_VERSION_UNSUPPORTED, HTTP_ERROR, UNEXPECTED_RESPONSE, UNKNOWN,
}

/**
 * Actionable connection diagnostic. Classification is best-effort; unknown errors remain [ConnectionProblem.UNKNOWN].
 *
 * @property problem Machine-readable category; NONE means the versioned ping returned OK.
 * @property message Summary without raw exception or response contents.
 * @property suggestion Next diagnostic step; never an automatic retry or configuration change.
 * @property httpStatus HTTP status when a response was received.
 * Reports produced by the SDK contain no socket path, raw exception, response body or headers.
 */
data class ConnectionDiagnostic(
    val problem: ConnectionProblem,
    val message: String,
    val suggestion: String,
    val httpStatus: Int? = null,
)

/**
 * Probes the configured daemon using the SDK's versioned ping without changing daemon state.
 * A successful probe establishes reachability/API acceptance, not permission for every Docker operation.
 * Does not retry or close this client. Cancellation from the caller always propagates.
 *
 * @param timeoutMillis Positive HTTP request/connect/socket timeout for this probe only.
 * @return Diagnostic with a category and actionable guidance; raw transport details are deliberately omitted.
 */
suspend fun DockerClient.diagnoseConnection(timeoutMillis: Long = 5_000): ConnectionDiagnostic {
    require(timeoutMillis > 0) { "timeoutMillis must be positive" }
    return try {
        client.prepareGet(apiPath("/_ping")) {
            attributes.put(DiagnosticProbe, Unit)
            timeout {
                requestTimeoutMillis = timeoutMillis
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            val bytes = ByteArray(4096)
            var size = 0
            while (size < bytes.size) {
                val read = channel.readAvailable(bytes, size, bytes.size - size)
                if (read < 0) break
                size += read
            }
            val text = bytes.decodeToString(0, size)
            val problem = when {
                response.status.isSuccess() && size < bytes.size && text.trim() == "OK" -> ConnectionProblem.NONE
                response.status.isSuccess() -> ConnectionProblem.UNEXPECTED_RESPONSE
                response.status.value == 401 || response.status.value == 403 -> ConnectionProblem.PERMISSION_DENIED
                response.status.value == 400 &&
                    text.contains("version", ignoreCase = true) &&
                    (text.contains("too new", ignoreCase = true) || text.contains("too old", ignoreCase = true)) ->
                    ConnectionProblem.API_VERSION_UNSUPPORTED
                else -> ConnectionProblem.HTTP_ERROR
            }
            diagnostic(problem, response.status.value)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        diagnoseFailure(failure)
    }
}

/**
 * Describes an upstream transport exception without retrying or modifying it.
 * Use for failures from HTTP calls, stream collection or exec/attach. Do not use to label application callback errors.
 * Recognizes common exception names and OS messages/codes; unsupported/localized messages remain UNKNOWN.
 * Cancellation is rethrown. Clean stream EOF has no exception and requires application-owned reconciliation.
 */
fun DockerClient.diagnoseFailure(failure: Throwable): ConnectionDiagnostic {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = failure
    while (current != null && chain.size < 32 && chain.none { it === current }) {
        if (current is CancellationException) throw current
        chain.add(current)
        current = current.cause
    }
    val names = chain.mapNotNull { it::class.simpleName }
    val text = chain.joinToString("\n") { it.message.orEmpty() }.lowercase()
    val nativeErrno = chain.firstNotNullOfOrNull {
        Regex("^(?:connect\\(.*\\)|Unix socket (?:read|write)) failed: errno=(\\d+)$")
            .matchEntire(it.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
    }
    val problem = when {
        nativeErrno == 110 || names.any { it.endsWith("TimeoutException") } -> ConnectionProblem.TIMEOUT
        nativeErrno in listOf(1, 13) || names.any { it == "AccessDeniedException" } ||
            text.contains("permission denied") || text.contains("eacces") -> ConnectionProblem.PERMISSION_DENIED
        nativeErrno == 2 || names.any { it == "NoSuchFileException" } ||
            text.contains("no such file or directory") || text.contains("enoent") -> ConnectionProblem.SOCKET_NOT_FOUND
        nativeErrno == 111 || text.contains("connection refused") || text.contains("econnrefused") -> ConnectionProblem.CONNECTION_REFUSED
        nativeErrno in listOf(32, 104) || text.contains("connection reset") || text.contains("broken pipe") ||
            text.contains("econnreset") || text.contains("epipe") -> ConnectionProblem.CONNECTION_LOST
        else -> ConnectionProblem.UNKNOWN
    }
    return diagnostic(problem)
}

private fun DockerClient.diagnostic(
    problem: ConnectionProblem,
    status: Int? = null,
): ConnectionDiagnostic {
    val (message, suggestion) = when (problem) {
        ConnectionProblem.NONE -> "Docker API ${DockerClient.API_VERSION} is reachable." to "The connection is ready for Docker requests."
        ConnectionProblem.SOCKET_NOT_FOUND -> "Docker socket was not found." to "Check the configured path and whether Docker is running; rootless Docker may use a different socket."
        ConnectionProblem.PERMISSION_DENIED -> "Access to Docker was denied." to "Check process permissions for the socket and any daemon authorization policy."
        ConnectionProblem.CONNECTION_REFUSED -> "No Docker listener accepted the connection." to "Check daemon state and whether the socket is stale or points to another Docker installation."
        ConnectionProblem.CONNECTION_LOST -> "The Docker connection was interrupted." to "Check daemon state, reconcile resources and explicitly reopen subscriptions; do not blindly replay mutations."
        ConnectionProblem.TIMEOUT -> "Docker did not respond within the probe or transport timeout." to "Check daemon load and socket configuration before choosing a longer timeout."
        ConnectionProblem.API_VERSION_UNSUPPORTED -> "Docker rejected API ${DockerClient.API_VERSION}." to "Use a daemon supporting API ${DockerClient.API_VERSION}; this SDK does not negotiate another version."
        ConnectionProblem.HTTP_ERROR -> "Docker returned HTTP $status." to "Check daemon logs and authorization/API configuration; this status alone does not establish a version mismatch."
        ConnectionProblem.UNEXPECTED_RESPONSE -> "The endpoint did not return a Docker ping response." to "Check that the socket belongs to the intended Docker daemon."
        ConnectionProblem.UNKNOWN -> "The Docker operation failed for an unclassified reason." to "Inspect daemon state and configuration; use caller-held exceptions only in trusted debugging."
    }
    return ConnectionDiagnostic(problem, message, suggestion, status)
}
