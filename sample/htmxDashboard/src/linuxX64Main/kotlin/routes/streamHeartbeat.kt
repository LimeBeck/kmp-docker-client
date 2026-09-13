package routes

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A disconnected HTTP consumer must also release an idle Docker subscription. */
suspend fun ByteWriteChannel.withHeartbeat(
    heartbeat: String = ": keepalive\n\n",
    block: suspend (send: suspend (String) -> Unit) -> Unit,
) = coroutineScope {
    val writes = Mutex()
    suspend fun send(text: String) = writes.withLock {
        writeStringUtf8(text)
        flush()
    }
    val monitor = launch {
        while (true) {
            delay(1_000)
            send(heartbeat)
        }
    }
    try { block(::send) }
    finally { monitor.cancelAndJoin() }
}
