package dev.limebeck.libs.docker.client.model

import dev.limebeck.libs.docker.client.diagnostics.*
import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.socket.DockerRawConnection
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Owned duplex exec/attach connection, independent of the client's HTTP connection pool.
 *
 * Collect exactly one of [incomingChunks] or [incoming], once. Completion, cancellation or failure of
 * that collection closes the connection. Close explicitly if output is never collected; `use` is supported.
 * Closing a connection does not guarantee that Docker terminates the remote process.
 *
 * @property isTty Whether output uses raw TTY bytes instead of Docker stdout/stderr multiplex framing.
 * @property connection Raw transport. Reading it directly bypasses buffered handshake data and output framing.
 */
@OptIn(ExperimentalAtomicApi::class)
class ExecSession internal constructor(
    incomingFlow: Flow<LogLine>,
    val isTty: Boolean,
    val connection: DockerRawConnection,
    chunkFlow: Flow<OutputChunk>
) : AutoCloseable {
    /** Compatibility constructor for a caller-provided text flow. Its chunks contain re-encoded text. */
    constructor(incomingFlow: Flow<LogLine>, isTty: Boolean, connection: DockerRawConnection) : this(
        incomingFlow, isTty, connection,
        incomingFlow.map { OutputChunk(it.type, it.line.encodeToByteArray()) }
    )

    @OptIn(ExperimentalUuidApi::class)
    private val sessionId = Uuid.generateV7().toString()

    init {
        DockerClient.logger.debug("ExecSession $sessionId started (tty = $isTty)")
    }

    internal var operationContext: DockerOperationContext? = null

    private val closed = AtomicBoolean(false)
    private val collection = Mutex()

    /** Compatibility output: TTY text waits for line endings. Use [incomingChunks] for terminals. */
    val incoming: Flow<LogLine> = owned(incomingFlow)

    /** Binary output, with Docker multiplex headers removed. Collect only once, or use [incoming]. */
    val incomingChunks: Flow<OutputChunk> = owned(chunkFlow)

    private fun <T> owned(source: Flow<T>): Flow<T> = flow {
        check(collection.tryLock()) { "Session output can only be collected once" }
        try {
            check(!closed.load()) { "Session is closed" }
            source.catch { failure ->
                throw operationContext?.let {
                    failure.withDockerContext(it.copy(stage = DockerFailureStage.SESSION_READ))
                } ?: failure
            }.collect { emit(it) }
        } finally {
            close()
        }
    }

    /**
     * Writes and flushes terminal input bytes without adding a newline.
     * @param bytes Input owned by the caller; coordinate concurrent writers.
     * @throws IllegalStateException If the session has already closed.
     */
    suspend fun send(bytes: ByteArray) {
        check(!closed.load()) { "Session is closed" }
        DockerClient.logger.trace("ExecSession $sessionId try to send ${bytes.size} bytes")
        try {
            connection.write.writeFully(bytes)
            connection.write.flush()
        } catch (failure: Exception) {
            throw operationContext?.let {
                failure.withDockerContext(it.copy(stage = DockerFailureStage.SESSION_WRITE))
            } ?: failure
        }
        DockerClient.logger.trace("ExecSession $sessionId sent ${bytes.size} bytes")
    }

    /**
     * Encodes [text] as UTF-8 and sends it without adding a newline.
     * @throws IllegalStateException If the session has already closed.
     */
    suspend fun send(text: String) = send(text.encodeToByteArray())

    /**
     * Closes the owned raw connection. Repeated calls are safe; subsequent sends or output collection fail.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        connection.close()
        DockerClient.logger.debug("ExecSession $sessionId closed")
    }

    override fun toString(): String {
        return "ExecSession($sessionId)"
    }
}
