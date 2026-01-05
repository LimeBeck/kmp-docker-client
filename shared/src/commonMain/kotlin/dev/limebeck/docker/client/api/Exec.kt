package dev.limebeck.docker.client.api

import dev.limebeck.docker.client.DockerClient
import dev.limebeck.docker.client.dslUtils.ApiCacheHolder
import dev.limebeck.docker.client.model.ErrorResponse
import dev.limebeck.docker.client.model.ExecInspectResponse
import dev.limebeck.docker.client.model.ExecStartConfig
import dev.limebeck.docker.client.model.Result
import io.ktor.client.request.*
import io.ktor.http.*

private object ExecKey

val DockerClient.exec: DockerExecApi
    get() = (this as ApiCacheHolder).apiCache.getOrPut(ExecKey) {
        DockerExecApi(this)
    } as DockerExecApi

class DockerExecApi(val dockerClient: DockerClient) {
    suspend fun start(
        id: String,
        config: ExecStartConfig = ExecStartConfig()
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/exec/$id/start") {
            contentType(ContentType.Application.Json)
            setBody(config)
        }.validateOnly()
    }

    suspend fun getInfo(id: String): Result<ExecInspectResponse, ErrorResponse> = with(dockerClient) {
        return client.get("/exec/$id/json").parse()
    }

    suspend fun resize(
        id: String,
        h: Int,
        w: Int
    ): Result<Unit, ErrorResponse> = with(dockerClient) {
        return client.post("/exec/$id/resize") {
            parameter("h", h.toString())
            parameter("w", w.toString())
        }.validateOnly()
    }
}
