package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.*
import dev.limebeck.libs.docker.client.model.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.test.*

class SwarmUnlockIntegrationTest {
    private fun client() = DockerClient(DockerClientConfig(
        connectionConfig= DockerClientConfig.ConnectionConfig.SocketConnection(SWARM_SOCKET)))

    @Test fun unlockAfterManagerRestart() = runBlocking {
        require(Regex("/tmp/kmp-rc\\.[A-Za-z0-9]+/docker\\.sock").matches(SWARM_SOCKET))
        val container="kmp-rc-"+SWARM_SOCKET.substringAfter("/tmp/kmp-rc.").substringBefore('/')
        fun command(vararg args: String): String {
            val process=ProcessBuilder(*args).redirectErrorStream(true).start()
            check(process.waitFor(60,TimeUnit.SECONDS)) { "Fixture command timed out" }
            val text=process.inputStream.bufferedReader().readText()
            check(process.exitValue()==0) { "Fixture command failed: $text" }
            return text.trim()
        }
        assertEquals("true",command("docker","inspect","--format","{{index .Config.Labels \"dev.limebeck.rc-acceptance\"}}",container))
        val key=client().use { docker ->
            val state=docker.swarm.getInfo().getOrThrow()
            docker.swarm.update(assertNotNull(state.version?.index),assertNotNull(state.spec).copy(
                encryptionConfig=SwarmSpecEncryptionConfig(autoLockManagers=true))).getOrThrow()
            assertNotNull(docker.swarm.getUnlockKey().getOrThrow().unlockKey)
        }
        command("docker","restart",container)
        withTimeout(30000) {
            while(true) {
                val ready=runCatching { command("docker","exec",container,"docker","-H","unix:///rc-run/docker.sock","info") }.isSuccess
                if(ready) break
                delay(250)
            }
        }
        command("docker","exec",container,"chmod","666","/rc-run/docker.sock")
        client().use { docker ->
            assertTrue(docker.swarm.getInfo().isError)
            assertTrue(docker.swarm.unlock(SwarmUnlockRequest(unlockKey="invalid")).isError)
            docker.swarm.unlock(SwarmUnlockRequest(unlockKey=key)).getOrThrow()
            withTimeout(15000) {
                while(docker.swarm.getInfo().isError) delay(200)
            }
            val state=docker.swarm.getInfo().getOrThrow()
            docker.swarm.update(assertNotNull(state.version?.index),assertNotNull(state.spec).copy(
                encryptionConfig=SwarmSpecEncryptionConfig(autoLockManagers=false))).getOrThrow()
        }
    }
}
