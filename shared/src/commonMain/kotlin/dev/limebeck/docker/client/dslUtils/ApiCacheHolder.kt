package dev.limebeck.docker.client.dslUtils

interface ApiCacheHolder {
    val apiCache: MutableMap<Any, Any>
}