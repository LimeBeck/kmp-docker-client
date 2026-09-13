package dev.limebeck.libs.docker.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Optional manifest information supplied in Docker push progress. */
@Serializable
data class ImagePushResult(
    @SerialName("Tag") val tag: String? = null,
    @SerialName("Digest") val digest: String? = null,
    @SerialName("Size") val size: ULong? = null,
)
