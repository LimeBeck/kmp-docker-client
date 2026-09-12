package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.utils.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#errors.streams.recovery */
class StreamReaderTest {
    @Test fun lineEndingsUnicodeAndFinalRecordArePreserved() = runTest {
        val channel = ByteReadChannel("Привет\r\n\nsolo\rlast".encodeToByteArray())
        assertEquals("Привет", channel.readDockerLine())
        assertEquals("", channel.readDockerLine())
        assertEquals("solo", channel.readDockerLine())
        assertEquals("last", channel.readDockerLine())
        assertNull(channel.readDockerLine())
    }

    @Test fun maximumLineIsAcceptedAndOversizedLineFails() = runTest {
        val text = "x".repeat(MAX_STREAM_RECORD_SIZE)
        assertEquals(text, ByteReadChannel(text.encodeToByteArray()).readDockerLine())
        assertFails { ByteReadChannel((text + "x").encodeToByteArray()).readDockerLine() }
    }

    @Test fun channelFailureIsNotCleanEof() = runTest {
        val channel = ByteChannel()
        channel.close(IllegalStateException("disconnected"))
        assertFails { channel.readDockerLine() }
    }

    @Test fun hugeLogFrameFailsBeforeReadingPayload() = runTest {
        val header = byteArrayOf(1, 0, 0, 0, 0x7f, -1, -1, -1)
        val channel = ByteChannel(autoFlush = true)
        channel.writeFully(header)
        try {
            assertFailsWith<IllegalStateException> { channel.readLogLines(false) { fail("Unexpected output") } }
        } finally { channel.cancel() }
    }

    @Test fun truncatedLogFramesFail() = runTest {
        for (bytes in listOf(byteArrayOf(1, 0), byteArrayOf(1, 0, 0, 0, 0, 0, 0, 2, 65))) {
            assertFails { ByteReadChannel(bytes).readLogLines(false) { fail("Unexpected output") } }
        }
    }

    @Test fun logConsumerFailurePropagates() = runTest {
        val expected = IllegalStateException("consumer")
        assertSame(expected, assertFailsWith<IllegalStateException> {
            ByteReadChannel("line\n".encodeToByteArray()).readLogLines(true) { throw expected }
        })
    }
}
