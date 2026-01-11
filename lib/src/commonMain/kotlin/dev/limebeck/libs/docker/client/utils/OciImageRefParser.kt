package dev.limebeck.libs.docker.client.utils

object OciImageRefParser {

    private val NAME_REGEX = Regex(
        pattern = "^[a-z0-9]+((\\.|_|__|-+)[a-z0-9]+)*(/[a-z0-9]+((\\.|_|__|-+)[a-z0-9]+)*)*$"
    )
    private val TAG_REGEX = Regex(
        pattern = "^[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}$"
    )
    private val DIGEST_REGEX = Regex(
        pattern = "^[A-Za-z][A-Za-z0-9]*(?:[+._-][A-Za-z0-9]+)*:[0-9a-fA-F]{32,}$"
    )

    data class Parsed(
        val registry: String?, // optional in input
        val name: String,      // OCI repository name
        val tag: String?,      // optional
        val digest: String?    // optional
    ) {
        init {
            require(!(tag != null && digest != null)) { "tag and digest are mutually exclusive" }
        }

        fun toRefString(): String = buildString {
            if (registry != null) append(registry).append('/')
            append(name)
            when {
                tag != null -> append(':').append(tag)
                digest != null -> append('@').append(digest)
            }
        }
    }

    data class Normalized(
        val registry: String,  // always present
        val name: String,      // may include implied "library/"
        val tag: String?,      // present if no digest (default "latest")
        val digest: String?    // present if provided
    ) {
        init {
            require(!(tag != null && digest != null)) { "tag and digest are mutually exclusive" }
        }

        /** Fully normalized ref string (registry always included). */
        fun toRefString(): String = buildString {
            append(registry).append('/').append(name)
            when {
                digest != null -> append('@').append(digest)
                tag != null -> append(':').append(tag)
            }
        }
    }

    fun parse(input: String): Parsed {
        val s = input.trim()
        require(s.isNotEmpty()) { "empty reference" }
        require(!s.contains(' ')) { "reference must not contain spaces" }

        val (beforeDigest, digest) = run {
            val at = s.indexOf('@')
            if (at < 0) s to null
            else {
                require(s.indexOf('@', at + 1) < 0) { "multiple '@' are not allowed" }
                val left = s.substring(0, at)
                val right = s.substring(at + 1)
                require(left.isNotEmpty()) { "missing name before '@'" }
                require(right.isNotEmpty()) { "missing digest after '@'" }
                left to right
            }
        }

        val (beforeTag, tag) = run {
            val lastSlash = beforeDigest.lastIndexOf('/')
            val lastColon = beforeDigest.lastIndexOf(':')
            if (lastColon > lastSlash) {
                val left = beforeDigest.substring(0, lastColon)
                val right = beforeDigest.substring(lastColon + 1)
                require(left.isNotEmpty()) { "missing name before ':'" }
                require(right.isNotEmpty()) { "missing tag after ':'" }
                left to right
            } else {
                beforeDigest to null
            }
        }

        val (registry, name) = splitRegistry(beforeTag)

        validateName(name)
        if (tag != null) validateTag(tag)
        if (digest != null) validateDigest(digest)

        return Parsed(registry = registry, name = name, tag = tag, digest = digest)
    }

    /**
     * Docker-like normalization:
     * - default registry: docker.io
     * - if registry == docker.io and name has no namespace => "library/<name>"
     * - default tag: latest (only when digest is absent)
     */
    fun normalize(input: String): Normalized = normalize(parse(input))

    fun normalize(parsed: Parsed): Normalized {
        val registry = parsed.registry ?: "docker.io"

        var name = parsed.name
        if (registry == "docker.io" && !name.contains('/')) {
            name = "library/$name"
        }

        val digest = parsed.digest
        val tag = when {
            digest != null -> null
            parsed.tag != null -> parsed.tag
            else -> "latest"
        }

        // Re-validate after implied edits
        validateName(name)
        if (tag != null) validateTag(tag)

        return Normalized(
            registry = registry,
            name = name,
            tag = tag,
            digest = digest
        )
    }

    private fun splitRegistry(path: String): Pair<String?, String> {
        val slash = path.indexOf('/')
        if (slash < 0) return null to path

        val first = path.substring(0, slash)
        val rest = path.substring(slash + 1)
        require(rest.isNotEmpty()) { "missing name after '/'" }

        val looksLikeRegistry = first == "localhost" || first.contains('.') || first.contains(':')
        return if (looksLikeRegistry) {
            validateRegistry(first)
            first to rest
        } else {
            null to path
        }
    }

    private fun validateName(name: String) {
        require(NAME_REGEX.matches(name)) { "invalid OCI name '$name'" }
    }

    private fun validateTag(tag: String) {
        require(TAG_REGEX.matches(tag)) { "invalid OCI tag '$tag'" }
    }

    private fun validateDigest(digest: String) {
        require(DIGEST_REGEX.matches(digest)) { "invalid digest '$digest' (expected algorithm:hex{32+})" }
    }

    private fun validateRegistry(registry: String) {
        require(registry.isNotEmpty()) { "empty registry" }
        require(!registry.contains('/')) { "registry must not contain '/'" }
        require(!registry.contains(' ')) { "registry must not contain spaces" }

        val colon = registry.lastIndexOf(':')
        if (colon >= 0) {
            val host = registry.substring(0, colon)
            val portStr = registry.substring(colon + 1)
            require(host.isNotEmpty()) { "registry host is empty" }
            require(portStr.isNotEmpty()) { "registry port is empty" }
            require(portStr.all { it.isDigit() }) { "registry port must be numeric" }
            val port = portStr.toIntOrNull()
            require(port != null && port in 1..65535) { "registry port must be in 1..65535" }
        }
    }
}
