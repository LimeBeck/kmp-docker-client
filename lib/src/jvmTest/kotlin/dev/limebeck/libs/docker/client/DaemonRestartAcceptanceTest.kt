package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.api.containers
import dev.limebeck.libs.docker.client.api.system
import dev.limebeck.libs.docker.client.api.exec
import dev.limebeck.libs.docker.client.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** spec://io.github.limebeck.kmp-docker-client/specs/ipc/FEAT-002.md#acceptance */
class DaemonRestartAcceptanceTest {
    private val daemon = requireNotNull(java.lang.System.getenv("RC_DOCKER_CONTAINER"))
    private val socket = requireNotNull(java.lang.System.getenv("RC_DOCKER_SOCKET"))

    private suspend fun docker(vararg args: String): String = withContext(Dispatchers.IO) {
        val output = Files.createTempFile("rc-docker-command", ".log")
        try {
            val process = ProcessBuilder(listOf("docker") + args)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                check(process.waitFor(60, TimeUnit.SECONDS)) { "Docker command timed out: ${args.firstOrNull()}" }
                val text = Files.readString(output)
                check(process.exitValue() == 0) { text }
                text.trim()
            } finally { if (process.isAlive) process.destroyForcibly().waitFor() }
        } finally { Files.deleteIfExists(output) }
    }

    private fun socketCount(): Long = Files.list(Path.of("/proc/self/fd")).use { files ->
        files.filter { runCatching { Files.readSymbolicLink(it).toString().startsWith("socket:") }.getOrDefault(false) }.count()
    }

    @Test fun streamsTerminateAndApplicationCanResubscribeAfterIsolatedDaemonRestart() = runBlocking {
        withTimeout(180_000) {
            require(daemon.startsWith("kmp-rc-"))
            assertEquals("true", docker("inspect", "--format", "{{index .Config.Labels \"dev.limebeck.rc-acceptance\"}}", daemon))
            val client = DockerClient(DockerClientConfig(
                connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(socket),
            ))
            try {
                val id = client.containers.create(config = ContainerCreateRequest(
                    image = "alpine:latest",
                    cmd = listOf("sh", "-c", "while true; do echo alive; echo diagnostic >&2; sleep 1; done"),
                    hostConfig = HostConfig(restartPolicy = RestartPolicy(name = RestartPolicy.Name.ALWAYS)),
                )).getOrThrow().id
                try {
                    client.containers.start(id).getOrThrow()
                    val logReady = CompletableDeferred<Unit>()
                    val statsReady = CompletableDeferred<Unit>()
                    val eventsReady = CompletableDeferred<Unit>()
                    var lastEventSeconds = "0"
                    val logs = client.containers.getLogs(id, ContainerLogsParameters(follow = true)).getOrThrow()
                    val stats = client.containers.getStats(id).getOrThrow()
                    // This is application-owned recovery: save a cursor and reconcile current state.
                    // Both clean EOF and an upstream error end the old subscription.
                    fun <T> Flow<T>.observe(ready: CompletableDeferred<Unit>, onValue: (T) -> Unit = {}): Deferred<String> = async {
                        var outcome = "EOF"
                        onEach { onValue(it); ready.complete(Unit) }
                            .catch { outcome = it::class.simpleName ?: "upstream error" }
                            .collect()
                        outcome
                    }
                    val oldLogs = logs.observe(logReady)
                    val oldStats = stats.observe(statsReady)
                    val oldEvents = client.system.events(since = "0", filters = mapOf("container" to listOf(id)))
                        .observe(eventsReady) { lastEventSeconds = it.time?.toString() ?: lastEventSeconds }
                    withTimeout(20_000) { logReady.await(); statsReady.await(); eventsReady.await() }
                    val exec = client.containers.execCreate(id, ExecConfig(
                        attachStdout = true, attachStderr = true, attachStdin = true,
                        tty = true, cmd = listOf("sh"),
                    )).getOrThrow()
                    val terminal = client.exec.startInteractive(exec.id, tty = true).getOrThrow()
                    val terminalReady = CompletableDeferred<Unit>()
                    val oldTerminal = terminal.incomingChunks.observe(terminalReady)
                    terminal.send("printf 'terminal-ready\\n'\n")
                    withTimeout(10_000) { terminalReady.await() }
                    try {
                        docker("restart", "--time", "3", daemon)
                        // Assert actual termination before cancelling anything; a timeout is a failure.
                        withTimeout(30_000) {
                            println("Old subscriptions: logs=${oldLogs.await()}, stats=${oldStats.await()}, events=${oldEvents.await()}, terminal=${oldTerminal.await()}")
                        }
                    } finally { terminal.close() }
                    // Restart recreates the temporary socket with daemon-owned permissions.
                    withTimeout(30_000) {
                        while (true) {
                            if (runCatching { docker("exec", daemon, "chmod", "666", "/rc-run/docker.sock"); client.system.ping().getOrThrow() }.isSuccess) break
                            delay(250)
                        }
                    }
                    withTimeout(20_000) {
                        while (client.containers.getInfo(id).getOrThrow().state?.running != true) delay(250)
                    }
                    // Reconcile state because finite event history cannot guarantee replay after restart.
                    assertTrue(client.containers.getList(all = true).getOrThrow().any { it.id == id })
                    assertEquals(id, withTimeout(10_000) { client.containers.getStats(id).getOrThrow().first().id })
                    assertTrue(withTimeout(10_000) {
                        client.containers.getLogs(id, ContainerLogsParameters(follow = true, tail = "1")).getOrThrow().first().line
                    }.isNotBlank())
                    val recoveredEvent = async(start = CoroutineStart.UNDISPATCHED) {
                        client.system.events(since = lastEventSeconds, filters = mapOf("container" to listOf(id)))
                            .first { it.action == "update" }
                    }
                    client.containers.update(id, ContainerUpdateRequest(
                        restartPolicy = RestartPolicy(name = RestartPolicy.Name.ON_FAILURE),
                    )).getOrThrow()
                    withTimeout(10_000) { assertEquals(id, recoveredEvent.await().actor?.ID) }
                    // Repeated partial collection and explicit close must release raw sockets.
                    suspend fun session() {
                        val next = client.containers.execCreate(id, ExecConfig(
                            attachStdout = true, attachStderr = true, tty = true,
                            cmd = listOf("sh", "-c", "printf 'Привет 🌍'; sleep 30"),
                        )).getOrThrow()
                        client.exec.startInteractive(next.id, tty = true).getOrThrow().use {
                            assertTrue(withTimeout(5_000) { it.incomingChunks.first().bytes }.isNotEmpty())
                        }
                    }
                    repeat(2) { session() }
                    delay(250)
                    val before = socketCount()
                    repeat(10) { session() }
                    withTimeout(5_000) { while (socketCount() > before + 2) delay(100) }
                    println("Recovery verified; socket count before=$before after=${socketCount()}")
                } finally { withContext(NonCancellable) { client.containers.remove(id, force = true).getOrThrow() } }
            } finally { client.client.close() }
        }
    }
}
