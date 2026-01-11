package dev.limebeck.libs.docker.client.model

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.socket.DockerRawConnection
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ExecSession(
    incomingFlow: Flow<LogLine>,
    val connection: DockerRawConnection
) : AutoCloseable {
    @OptIn(ExperimentalUuidApi::class)
    private val sessionId = Uuid.generateV7().toString()

    init {
        DockerClient.logger.debug { "ExecSession $sessionId started" }
    }

    val incoming = incomingFlow.onEach {
        DockerClient.logger.debug { "ExecSession $sessionId received $it" }
    }

    suspend fun send(bytes: ByteArray) {
        DockerClient.logger.debug { "ExecSession $sessionId try to send ${bytes.size} bytes" }
        connection.write.writeFully(bytes)
        connection.write.flush()
        DockerClient.logger.debug { "ExecSession $sessionId sent ${bytes.size} bytes" }
    }

    suspend fun send(text: String) = send(text.encodeToByteArray())

    override fun close() {
        connection.close()
        DockerClient.logger.debug { "ExecSession $sessionId closed" }
    }

    override fun toString(): String {
        return "ExecSession($sessionId)"
    }
}
