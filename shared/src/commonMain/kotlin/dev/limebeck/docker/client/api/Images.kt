package dev.limebeck.docker.client.api

import dev.limebeck.docker.client.DockerClient
import dev.limebeck.docker.client.dslUtils.ApiCacheHolder

private object ImagesKey

val DockerClient.images
    get() = (this as ApiCacheHolder).apiCache.getOrPut(ImagesKey) {
        DockerImagesApi(this)
    } as DockerImagesApi

class DockerImagesApi(dockerClient: DockerClient) {

}