package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.model.ContainerInspectResponse
import dev.limebeck.libs.docker.client.model.ImageInspect
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-001.md#models.storage */
class DriverDataCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun containerdStorageMetadataCanBeNullForContainersAndImages() {
        val response = """{"GraphDriver":{"Name":"overlayfs","Data":null}}"""
        val container = json.decodeFromString<ContainerInspectResponse>(response)
        val image = json.decodeFromString<ImageInspect>(response)
        assertEquals("overlayfs", container.graphDriver?.name)
        assertEquals("overlayfs", image.graphDriver?.name)
        assertNull(container.graphDriver?.data)
        assertNull(image.graphDriver?.data)
    }

    @Test fun classicStorageMetadataIsPreserved() {
        val response = """{"GraphDriver":{"Name":"overlay2","Data":{"MergedDir":"/var/lib/docker/example"}}}"""
        val expected = mapOf("MergedDir" to "/var/lib/docker/example")
        assertEquals(expected, json.decodeFromString<ContainerInspectResponse>(response).graphDriver?.data)
        assertEquals(expected, json.decodeFromString<ImageInspect>(response).graphDriver?.data)
    }
}
