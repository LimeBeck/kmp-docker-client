package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.socket.DockerRawConnection
import dev.limebeck.libs.docker.client.utils.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#exec.streams */
class TerminalSessionTest {
    private class Connection : DockerRawConnection {
        override val read = ByteChannel(autoFlush = true)
        override val write = ByteChannel(autoFlush = true)
        var closes = 0
        override fun close() {
            closes++
            read.cancel()
            write.cancel()
        }
    }

    private fun session(connection: Connection, tty: Boolean = true, prefix: ByteArray = byteArrayOf()) =
        ExecSession(emptyFlow(), tty, connection, flow {
            withPrefixedChannel(prefix, connection.read) { channel ->
                channel.readOutputChunks(tty) { emit(it) }
            }
        })

    private fun frame(type: Int, payload: ByteArray): ByteArray = byteArrayOf(
        type.toByte(), 0, 0, 0,
        (payload.size ushr 24).toByte(), (payload.size ushr 16).toByte(),
        (payload.size ushr 8).toByte(), payload.size.toByte()
    ) + payload

    @Test fun promptWithoutNewlineArrivesBeforeEofAndFirstClosesSession() = runTest {
        val connection = Connection()
        val session = session(connection)
        connection.read.writeFully("\u001b[32mready> \r".encodeToByteArray())
        assertEquals("\u001b[32mready> \r", session.incomingChunks.first().bytes.decodeToString())
        session.close()
        assertEquals(1, connection.closes)
        assertFailsWith<IllegalStateException> { session.send("late") }
    }

    @Test fun handshakePrefixIsDeliveredWithoutWaitingForUpstream() = runTest {
        val connection = Connection()
        val session = session(connection, prefix = "prompt> ".encodeToByteArray())
        assertEquals("prompt> ", session.incomingChunks.first().bytes.decodeToString())
        assertEquals(1, connection.closes)
    }

    @Test fun splitFramesAndSplitUtf8PreserveAllBytesAndStreamTypes() = runTest {
        val connection = Connection()
        val text = "Привет 🌍".encodeToByteArray()
        val bytes = frame(1, text) + frame(2, byteArrayOf(13, 10, 0, -1))
        val session = session(connection, tty = false, prefix = bytes.copyOfRange(0, 3))
        val writer = launch {
            for (byte in bytes.drop(3)) {
                connection.read.writeByte(byte)
                yield()
            }
            connection.read.close()
        }
        val chunks = session.incomingChunks.toList()
        assertContentEquals(text, chunks.filter { it.type == LogLine.Type.STDOUT }.flatMap { it.bytes.toList() }.toByteArray())
        assertContentEquals(byteArrayOf(13, 10, 0, -1), chunks.filter { it.type == LogLine.Type.STDERR }.flatMap { it.bytes.toList() }.toByteArray())
        writer.join()
        assertEquals(1, connection.closes)
    }

    @Test fun largeFrameIsReadInBoundedChunks() = runTest {
        val payload = ByteArray(100_000) { it.toByte() }
        val chunks = mutableListOf<OutputChunk>()
        ByteReadChannel(frame(1, payload)).readOutputChunks(false) { chunks.add(it) }
        assertTrue(chunks.all { it.bytes.size in 1..16384 })
        assertContentEquals(payload, chunks.flatMap { it.bytes.toList() }.toByteArray())
    }

    @Test fun cleanEofAndEmptyFramesCompleteNormally() = runTest {
        val chunks = mutableListOf<OutputChunk>()
        ByteReadChannel(frame(1, byteArrayOf())).readOutputChunks(false) { chunks.add(it) }
        assertTrue(chunks.isEmpty())
    }

    @Test fun truncatedHeaderAndPayloadFailAndClose() = runTest {
        for (bytes in listOf(byteArrayOf(1, 0), frame(1, byteArrayOf(42)).dropLast(1).toByteArray())) {
            val connection = Connection()
            connection.read.writeFully(bytes)
            connection.read.close()
            assertFails { session(connection, tty = false).incomingChunks.collect() }
            assertEquals(1, connection.closes)
        }
    }

    @Test fun invalidMultiplexHeaderFails() = runTest {
        for (bytes in listOf(frame(9, byteArrayOf()), byteArrayOf(1, 2, 0, 0, 0, 0, 0, 0))) {
            assertFailsWith<IllegalStateException> {
                ByteReadChannel(bytes).readOutputChunks(false) { error("Unexpected output") }
            }
        }
    }

    @Test fun consumerFailurePropagatesAndCloses() = runTest {
        val connection = Connection()
        val expected = IllegalStateException("consumer failed")
        val actual = assertFailsWith<IllegalStateException> {
            session(connection, prefix = byteArrayOf(1)).incomingChunks.collect { throw expected }
        }
        assertTrue(actual === expected || actual.cause === expected)
        assertEquals(1, connection.closes)
    }

    @Test fun transportFailurePropagatesThroughPrefixForwarder() = runTest {
        val connection = Connection()
        val expected = IllegalStateException("transport failed")
        connection.read.close(expected)
        val actual = assertFails {
            session(connection, prefix = byteArrayOf(1)).incomingChunks.collect()
        }
        assertEquals(expected.message, actual.message)
        assertEquals(1, connection.closes)
    }

    @Test fun cancellingIdleCollectionClosesConnection() = runTest {
        val connection = Connection()
        val session = session(connection)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { session.incomingChunks.collect() }
        collector.cancelAndJoin()
        assertEquals(1, connection.closes)
    }

    @Test fun secondCollectorCannotStealOrCloseFirstCollectorsConnection() = runTest {
        val connection = Connection()
        val session = session(connection)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { session.incomingChunks.collect() }
        assertFailsWith<IllegalStateException> { session.incoming.collect() }
        assertEquals(0, connection.closes)
        collector.cancelAndJoin()
        assertEquals(1, connection.closes)
    }

    @Test fun closeWithoutCollectionIsIdempotent() = runTest {
        val connection = Connection()
        val session = session(connection, prefix = byteArrayOf(1))
        session.close()
        session.close()
        assertEquals(1, connection.closes)
        assertFailsWith<IllegalStateException> { session.incomingChunks.collect() }
        assertEquals(1, connection.closes)
    }
}
