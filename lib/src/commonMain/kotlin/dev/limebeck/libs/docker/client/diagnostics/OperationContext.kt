package dev.limebeck.libs.docker.client.diagnostics

import dev.limebeck.libs.docker.client.DockerClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import kotlinx.coroutines.CancellationException

/** Stage where the SDK observed a failure, not a claim about its root cause. */
enum class DockerFailureStage { REQUEST, RESPONSE, STREAM, CONNECT, HANDSHAKE, SESSION_READ, SESSION_WRITE }

/**
 * Safe metadata attached to an SDK exception. SDK-produced contexts omit socket paths, resource identifiers,
 * query parameters, headers, payloads and exception messages. The original exception itself remains sensitive.
 *
 * @property method HTTP verb, or UNKNOWN for an unrecognized method.
 * @property route Allowlisted route template, such as /containers/{resource}/stats; unknown routes use /{unknown}.
 * @property apiVersion Fixed API version used by this SDK.
 * @property stage Boundary where the SDK observed the failure; it does not establish whether a retry is safe.
 * @property httpStatus Response status when available; null when no response status was captured.
 */
data class DockerOperationContext(
    val method: String,
    val route: String,
    val apiVersion: String,
    val stage: DockerFailureStage,
    val httpStatus: Int? = null,
)

/** Safe metadata marker stored in suppressed exceptions; its message contains only [context]. */
class DockerContextException internal constructor(val context: DockerOperationContext) :
    Exception("Docker operation: $context")

/**
 * Context attached by the SDK, including through coroutine-recovered cause chains, or null when absent.
 * Read this instead of printing the original exception when producing user-facing diagnostics.
 * Cancellation and failures outside SDK boundaries may have no context.
 *
 * @sample dev.limebeck.libs.docker.guide.inspectWithContext
 */
val Throwable.dockerContext: DockerOperationContext?
    get() {
        if (this is CancellationException) return null
        val visited = mutableListOf<Throwable>()
        var current: Throwable? = this
        while (current != null && visited.size < 32 && visited.none { it === current }) {
            current.suppressedExceptions.filterIsInstance<DockerContextException>().lastOrNull()?.let { return it.context }
            visited.add(current)
            current = current.cause
        }
        return null
    }

@PublishedApi
internal fun Throwable.withDockerContext(context: DockerOperationContext): Throwable {
    if (this !is CancellationException && dockerContext == null) addSuppressed(DockerContextException(context))
    return this
}

@PublishedApi
internal fun HttpResponse.operationContext(stage: DockerFailureStage): DockerOperationContext =
    operationContext(request.method.value, request.url.encodedPath, stage, status.value)

@PublishedApi
internal fun operationContext(
    method: String,
    path: String,
    stage: DockerFailureStage,
    status: Int? = null,
): DockerOperationContext {
    val route = path.substringBefore('?').removePrefix("/v${DockerClient.API_VERSION}")
    // TODO: Replace this closed route allowlist with explicit safe route metadata supplied by API
    // extensions. The API is intentionally extensible; diagnostics must not require editing a central
    // route registry for each extension. Keep /{unknown} when metadata is absent; never expose raw paths.
    val safeRoute = when {
        route in setOf("/_ping", "/version", "/info", "/auth", "/events", "/system/df",
            "/containers/json", "/containers/create", "/containers/prune", "/images/json",
            "/images/create", "/images/search", "/images/prune", "/images/load", "/images/get",
            "/volumes", "/volumes/create", "/volumes/prune", "/networks", "/networks/create", "/networks/prune") -> route
        else -> {
            val parts = route.split('/')
            val group = parts.getOrNull(1)
            val action = parts.getOrNull(3)
            val actions = when (group) {
                "containers" -> setOf("json", "logs", "start", "stop", "restart", "kill", "update", "rename",
                    "pause", "unpause", "top", "changes", "stats", "resize", "wait", "export", "archive", "exec", "attach")
                "exec" -> setOf("start", "json", "resize")
                "images" -> setOf("json", "history", "push", "tag", "get")
                "networks" -> setOf("connect", "disconnect")
                else -> emptySet()
            }
            when {
                parts.size == 4 && action in actions -> "/$group/{resource}/$action"
                parts.size == 3 && group in setOf("containers", "images", "volumes", "networks") -> "/$group/{resource}"
                else -> "/{unknown}"
            }
        }
    }
    return DockerOperationContext(
        method.takeIf { it in setOf("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") } ?: "UNKNOWN",
        safeRoute, DockerClient.API_VERSION, stage, status,
    )
}

/**
 * Failure raised by getOrThrow on a contextual SDK error Result. Remains an IllegalStateException.
 * [error] and the original cause are retained for trusted debugging; the message contains only safe context.
 */
class DockerResultException internal constructor(
    val error: Any?,
    context: DockerOperationContext,
    cause: Throwable?,
) : IllegalStateException("Docker operation failed: $context", cause)
