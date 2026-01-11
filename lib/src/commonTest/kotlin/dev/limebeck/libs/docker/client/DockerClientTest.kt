package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.model.LogLine
import dev.limebeck.libs.docker.client.utils.readLogLines
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DockerClientTest {
    @Test
    fun testReadLogLinesWithoutTty() = runTest {
        // Docker log format: [8]byte header + payload
        // Header: [0] stream type, [1-3] zero, [4-7] payload size (big endian)
        val streamType = 1.toByte() // STDOUT
        val payload = "Hello, Docker!".encodeToByteArray()
        val size = payload.size
        val header = ByteArray(8).apply {
            this[0] = streamType
            this[4] = (size shr 24).toByte()
            this[5] = (size shr 16).toByte()
            this[6] = (size shr 8).toByte()
            this[7] = size.toByte()
        }

        val channel = ByteReadChannel(header + payload)
        val logs = flow {
            channel.readLogLines(isTty = false, this@flow)
        }.toList()

        assertEquals(1, logs.size)
        assertEquals("Hello, Docker!", logs[0].line)
        assertEquals(LogLine.Type.STDOUT, logs[0].type)
    }

    @Test
    fun testReadLogLinesWithTty() = runTest {
        val payload = "Hello, TTY!\nSecond line".encodeToByteArray()

        val channel = ByteReadChannel(payload)
        val logs = flow {
            channel.readLogLines(isTty = true, this@flow)
        }.toList()

        assertEquals(2, logs.size)
        assertEquals("Hello, TTY!", logs[0].line)
        assertEquals("Second line", logs[1].line)
    }
}
