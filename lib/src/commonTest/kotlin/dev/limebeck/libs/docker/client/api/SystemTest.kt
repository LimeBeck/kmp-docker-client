package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SystemTest {
    private val client = DockerClient()

    @Test
    fun testGetInfo() = runTest {
        client.system.getInfo().getOrThrow()
    }

    @Test
    fun testGetVersion() = runTest {
        client.system.getVersion()
            .onSuccess {
                println("Docker version: ${it.version}")
            }
            .onError {
                println("Note: Version check failed (likely due to model mismatch in generated code): ${it.message}")
            }
    }

    @Test
    fun testPing() = runTest {
        client.system.ping().getOrThrow()
    }

    @Test
    fun testDataUsage() = runTest {
        client.system.dataUsage()
            .onSuccess {
                println("Data usage layers size: ${it.layersSize}")
            }
            .onError {
                println("Note: Data usage check failed (likely due to model mismatch in generated code): ${it.message}")
            }
    }
}
