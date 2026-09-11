package routes

import dev.limebeck.libs.docker.client.model.ExecSession
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal suspend fun DefaultWebSocketServerSession.bridgeTerminal(session: ExecSession) = coroutineScope {
    val output = launch {
        session.incomingChunks.collect { send(Frame.Binary(true, it.bytes)) }
        close(CloseReason(CloseReason.Codes.NORMAL, "Terminal finished"))
    }
    try {
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> session.send(frame.readText())
                is Frame.Binary -> session.send(frame.data)
                else -> Unit
            }
        }
    } finally {
        session.close()
        output.cancel()
    }
}
