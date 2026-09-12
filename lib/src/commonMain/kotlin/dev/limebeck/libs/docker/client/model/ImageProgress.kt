package dev.limebeck.libs.docker.client.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One Docker image-operation record. Fields may be absent; [raw] preserves extensions.
 * Counts describe the individual layer [id], not overall operation completion.
 * The operation's returned Result determines success, never a status string or percentage.
 */
data class ImageProgress(val raw: JsonObject) {
    val id: String? get() = text("id")
    val status: String? get() = text("status")
    val stream: String? get() = text("stream")
    val progress: String? get() = text("progress")
    val current: ULong? get() = count("current")
    val total: ULong? get() = count("total")
    val aux: JsonObject? get() = raw["aux"] as? JsonObject

    private fun text(key: String): String? = (raw[key] as? JsonPrimitive)?.contentOrNull

    private fun count(key: String): ULong? =
        ((raw["progressDetail"] as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull?.toULongOrNull()
}
