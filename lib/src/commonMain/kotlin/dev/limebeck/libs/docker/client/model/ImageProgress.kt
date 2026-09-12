package dev.limebeck.libs.docker.client.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One Docker image-operation record. Fields may be absent.
 * Counts describe the individual layer [id], not overall operation completion.
 * The operation's returned Result determines success, never a status string or percentage.
 */
@Serializable
data class ImageProgress(
    val id: String? = null,
    val status: String? = null,
    val stream: String? = null,
    val progress: String? = null,
    val progressDetail: ImageProgressDetail? = null,
    val aux: JsonObject? = null,
)
