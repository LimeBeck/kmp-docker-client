package dev.limebeck.docker.client

import dev.limebeck.docker.client.dslUtils.OciImageRefParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OciImageRefParserTest {

    @Test
    fun `parse should correctly handle image with registry name and tag`() {
        val input = "registry.example.com/my-image:1.0.0"
        val parsed = OciImageRefParser.parse(input)
        assertEquals("registry.example.com", parsed.registry)
        assertEquals("my-image", parsed.name)
        assertEquals("1.0.0", parsed.tag)
        assertEquals(null, parsed.digest)
    }

    @Test
    fun `parse should correctly handle image with only name and tag`() {
        val input = "my-image:latest"
        val parsed = OciImageRefParser.parse(input)
        assertEquals(null, parsed.registry)
        assertEquals("my-image", parsed.name)
        assertEquals("latest", parsed.tag)
        assertEquals(null, parsed.digest)
    }

    @Test
    fun `parse should correctly handle image with registry name and digest`() {
        val input =
            "registry.example.com/my-image@sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        val parsed = OciImageRefParser.parse(input)
        assertEquals("registry.example.com", parsed.registry)
        assertEquals("my-image", parsed.name)
        assertEquals(null, parsed.tag)
        assertEquals("sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", parsed.digest)
    }

    @Test
    fun `parse should correctly handle image with only name`() {
        val input = "my-image"
        val parsed = OciImageRefParser.parse(input)
        assertEquals(null, parsed.registry)
        assertEquals("my-image", parsed.name)
        assertEquals(null, parsed.tag)
        assertEquals(null, parsed.digest)
    }

    @Test
    fun `parse should fail for image with both tag and digest`() {
        val input = "my-image:1.0.0@sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        val exception = assertFailsWith<IllegalArgumentException> {
            OciImageRefParser.parse(input)
        }
        assertEquals("tag and digest are mutually exclusive", exception.message)
    }

    @Test
    fun `parse should fail for empty input`() {
        val input = ""
        val exception = assertFailsWith<IllegalArgumentException> {
            OciImageRefParser.parse(input)
        }
        assertEquals("empty reference", exception.message)
    }

    @Test
    fun `parse should fail for input with multiple at symbols`() {
        val input = "my-image@sha256:abcdef@sha256:ghijkl"
        val exception = assertFailsWith<IllegalArgumentException> {
            OciImageRefParser.parse(input)
        }
        assertEquals("multiple '@' are not allowed", exception.message)
    }

    @Test
    fun `parse should fail for input with spaces`() {
        val input = "my image :1.0.0"
        val exception = assertFailsWith<IllegalArgumentException> {
            OciImageRefParser.parse(input)
        }
        assertEquals("reference must not contain spaces", exception.message)
    }

    @Test
    fun `parse should fail for invalid digest`() {
        val input = "my-image@invalid-digest"
        val exception = assertFailsWith<IllegalArgumentException> {
            OciImageRefParser.parse(input)
        }
        assertEquals("invalid digest 'invalid-digest' (expected algorithm:hex{32+})", exception.message)
    }

    @Test
    fun `parse should fail for invalid tag`() {
        val input = "my-image:invalid tag"
        val exception = assertFailsWith<IllegalArgumentException> {
            OciImageRefParser.parse(input)
        }
        assertEquals("reference must not contain spaces", exception.message)
    }

    @Test
    fun `parse should fail for missing name before colon`() {
        val input = ":latest"
        val exception = assertFailsWith<IllegalArgumentException> {
            OciImageRefParser.parse(input)
        }
        assertEquals("missing name before ':'", exception.message)
    }

    @Test
    fun `parse should fail for missing name before digest`() {
        val input = "@sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        val exception = assertFailsWith<IllegalArgumentException> {
            OciImageRefParser.parse(input)
        }
        assertEquals("missing name before '@'", exception.message)
    }

    @Test
    fun `name normalization should return docker io for empty repository`() {
        val input = "library/my-image:latest"
        assertEquals("docker.io/library/my-image:latest", OciImageRefParser.normalize(input).toRefString())
    }

    @Test
    fun `name normalization should return library for empty docker io namespace`() {
        val input = "my-image:latest"
        assertEquals("docker.io/library/my-image:latest", OciImageRefParser.normalize(input).toRefString())
    }

    @Test
    fun `name normalization should return latest tag for empty tag`() {
        val input = "docker.io/library/my-image"
        assertEquals("docker.io/library/my-image:latest", OciImageRefParser.normalize(input).toRefString())
    }

    @Test
    fun `name normalization should return docker io library and latest tag`() {
        val input = "my-image"
        assertEquals("docker.io/library/my-image:latest", OciImageRefParser.normalize(input).toRefString())
    }
}