package dev.limebeck.libs.docker.client.model

import kotlinx.serialization.Serializable

/**
 * One Docker image-operation record. Fields may be absent.
 * [TAux] is selected by the operation: [ImagePushResult] for push, [Unit] for create/load.
 * Counts describe the individual layer [id], not overall operation completion.
 * The operation's returned Result determines success, never a status string or percentage.
 */
@Serializable
data class ImageProgress<out TAux>(
    val id: String? = null,
    val status: String? = null,
    val stream: String? = null,
    val progress: String? = null,
    val progressDetail: ImageProgressDetail? = null,
    val aux: TAux? = null,
)
