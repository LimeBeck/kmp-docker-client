package dev.limebeck.libs.docker.client.api.swarm

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.utils.readLogLines
import io.ktor.client.request.*
import kotlinx.coroutines.flow.*

internal fun DockerClient.swarmLogs(path: String, tty: Boolean, parameters: SwarmLogsParameters): Flow<LogLine> = channelFlow {
    client.prepareGet(apiPath(path)) {
        parameter("follow", parameters.follow)
        parameter("stdout", parameters.stdout)
        parameter("stderr", parameters.stderr)
        parameter("timestamps", parameters.timestamps)
        parameter("details", parameters.details)
        parameters.since?.let { parameter("since", it) }
        parameters.tail?.let { parameter("tail", it) }
        applyStreamConfig()
    }.execute { response ->
        response.consumeStream { channel -> channel.readLogLines(tty) { send(it) } }
    }
}.buffer(0)
