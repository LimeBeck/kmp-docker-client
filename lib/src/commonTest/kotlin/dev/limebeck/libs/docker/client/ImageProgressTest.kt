package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.api.images
import dev.limebeck.libs.docker.client.model.ImageProgressDetail
import dev.limebeck.libs.docker.client.model.ImageProgress
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlin.test.*

class ImageProgressTest {
    @Test fun pullAndLoadReportProgressAgainstDocker() = runTest {
        val client = DockerClient()
        try {
            var pullRecords = 0
            client.images.create("alpine:latest") { pullRecords++ }.getOrThrow()
            assertTrue(pullRecords > 0)
            val expected = client.images.inspect("alpine:latest").getOrThrow().id
            val archive = client.images.export("alpine:latest").getOrThrow()
            var loadRecords = 0
            client.images.load(body = archive) { loadRecords++ }.getOrThrow()
            assertTrue(loadRecords > 0)
            assertEquals(expected, client.images.inspect("alpine:latest").getOrThrow().id)
        } finally {
            client.client.close()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun countsRemainExactAcrossPlatformsAndUnknownFieldsAreIgnored() {
        val progress = json.decodeFromString<ImageProgress>("""{"id":"layer","progressDetail":{"current":9007199254740993,"total":18446744073709551615,"future":true},"unknown":{"value":true}}""")
        val expected = ImageProgress(id = "layer", progressDetail = ImageProgressDetail(9007199254740993uL, ULong.MAX_VALUE))
        assertEquals(expected, progress)
        assertEquals(expected, json.decodeFromString<ImageProgress>(json.encodeToString(progress)))
        assertEquals("done", progress.copy(status = "done").status)
    }

    @Test fun absentCountsRemainUnknown() {
        assertEquals(ImageProgress(), json.decodeFromString<ImageProgress>("{}"))
        for (detail in listOf("null", "{}", "{\"current\":null,\"total\":null}")) {
            val progress = json.decodeFromString<ImageProgress>("{\"progressDetail\":$detail}")
            assertNull(progress.progressDetail?.current)
            assertNull(progress.progressDetail?.total)
        }
    }

    @Test fun invalidCountsAreRejected() {
        for (detail in listOf("{\"current\":-1}", "{\"total\":1.5}", "{\"current\":\"invalid\"}", "{\"total\":18446744073709551616}")) {
            assertFailsWith<SerializationException> {
                json.decodeFromString<ImageProgress>("{\"progressDetail\":$detail}")
            }
        }
    }
}
