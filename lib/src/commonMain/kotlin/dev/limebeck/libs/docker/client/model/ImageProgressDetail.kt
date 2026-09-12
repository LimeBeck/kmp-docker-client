package dev.limebeck.libs.docker.client.model

import kotlinx.serialization.Serializable

/** Exact per-layer counts. Missing counts are unknown; a zero total is not a usable denominator. */
@Serializable
data class ImageProgressDetail(
    val current: ULong? = null,
    val total: ULong? = null,
)
