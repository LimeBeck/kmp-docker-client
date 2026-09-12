package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.api.images
import dev.limebeck.libs.docker.client.model.ImageProgress
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

    @Test fun countsRemainExactAcrossPlatformsAndExtensionsAreRetained() {
        val raw = Json.parseToJsonElement("""{"id":"layer","progressDetail":{"current":9007199254740993,"total":18446744073709551615},"unknown":{"value":true}}""").jsonObject
        val progress = ImageProgress(raw)
        assertEquals(9007199254740993uL, progress.current)
        assertEquals(ULong.MAX_VALUE, progress.total)
        assertEquals("layer", progress.id)
        assertEquals(raw, progress.raw)
        assertNull(progress.status)
    }

    @Test fun absentOrInvalidCountsRemainUnknown() {
        for (detail in listOf("null", "{}", "{\"current\":-1,\"total\":1.5}", "{\"current\":\"invalid\",\"total\":18446744073709551616}")) {
            val progress = ImageProgress(Json.parseToJsonElement("{\"progressDetail\":$detail}").jsonObject)
            assertNull(progress.current)
            assertNull(progress.total)
        }
    }
}
