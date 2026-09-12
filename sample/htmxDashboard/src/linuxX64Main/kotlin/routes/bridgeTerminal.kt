package routes

import dev.limebeck.libs.docker.client.model.ExecSession
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

internal suspend fun DefaultWebSocketServerSession.bridgeTerminal(
    session: ExecSession,
    resize: suspend (rows: Int, cols: Int) -> Unit,
) = coroutineScope {
    val output = launch {
        session.incomingChunks.collect { send(Frame.Binary(true, it.bytes)) }
        close(CloseReason(CloseReason.Codes.NORMAL, "Terminal finished"))
    }
    try {
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    val control = Json.parseToJsonElement(frame.readText()).jsonObject
                    require(control["type"]?.jsonPrimitive?.content == "resize") { "Unknown terminal control" }
                    val rows = control["rows"]?.jsonPrimitive?.intOrNull
                    val cols = control["cols"]?.jsonPrimitive?.intOrNull
                    require(rows != null && rows in 1..1000 && cols != null && cols in 1..1000) {
                        "Terminal dimensions must be between 1 and 1000"
                    }
                    resize(rows, cols)
                }
                is Frame.Binary -> session.send(frame.data)
                else -> Unit
            }
        }
    } finally {
        session.close()
        output.cancel()
    }
}
