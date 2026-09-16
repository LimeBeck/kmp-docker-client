package dev.limebeck.libs.docker.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** ID returned by Swarm secret/config creation (Docker uses the uppercase JSON key ID). */
@Serializable
data class SwarmCreateResponse(@SerialName("ID") val id: String)
