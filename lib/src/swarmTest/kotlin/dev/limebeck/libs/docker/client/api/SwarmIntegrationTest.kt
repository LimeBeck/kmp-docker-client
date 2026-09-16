package dev.limebeck.libs.docker.client.api

import dev.limebeck.libs.docker.client.DockerClient
import dev.limebeck.libs.docker.client.DockerClientConfig
import dev.limebeck.libs.docker.client.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.random.Random

class SwarmIntegrationTest {
    private fun client() = DockerClient(DockerClientConfig(
        connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(SWARM_SOCKET)))

    @Test fun clusterAndNodeUpdates() = runTest(timeout=90.seconds) {
        client().use { docker ->
            val api=docker.swarm
            val cluster=api.getInfo().getOrThrow()
            val spec=assertNotNull(cluster.spec).copy(labels=mapOf("sdk-test" to "cluster"))
            val version=assertNotNull(cluster.version?.index)
            api.update(version,spec,rotateWorkerToken=true).getOrThrow()
            val after=api.getInfo().getOrThrow()
            assertEquals("cluster",after.spec?.labels?.get("sdk-test"))
            assertNotEquals(cluster.joinTokens?.worker,after.joinTokens?.worker)
            assertTrue(api.update(version,spec).isError)
            val node=api.nodes.getList().getOrThrow().single()
            val id=assertNotNull(node.ID)
            val inspected=api.nodes.getInfo(id).getOrThrow()
            val nodeVersion=assertNotNull(inspected.version?.index)
            val nodeSpec=assertNotNull(inspected.spec).copy(labels=mapOf("sdk-test" to "node"))
            api.nodes.update(id,nodeVersion,nodeSpec).getOrThrow()
            assertEquals("node",api.nodes.getInfo(id).getOrThrow().spec?.labels?.get("sdk-test"))
            assertEquals(listOf(id),api.nodes.getList(mapOf("node.label" to listOf("sdk-test=node"))).getOrThrow().map{it.ID})
            assertTrue(api.nodes.update(id,nodeVersion,nodeSpec).isError)
            assertTrue(api.nodes.remove(id).isError,"Active manager cannot be removed")
            assertTrue(api.init(SwarmInitRequest(listenAddr="0.0.0.0:2377",advertiseAddr="eth0")).isError)
            api.leave(force=true).getOrThrow()
            assertTrue(api.getInfo().isError)
            val newId=api.init(SwarmInitRequest(listenAddr="0.0.0.0:2377",advertiseAddr="eth0",spec=SwarmSpec(encryptionConfig=SwarmSpecEncryptionConfig(autoLockManagers=true)))).getOrThrow()
            assertTrue(newId.isNotBlank())
            assertTrue(assertNotNull(api.getUnlockKey().getOrThrow().unlockKey).startsWith("SWMKEY-"))
            val lockedSpec=api.getInfo().getOrThrow()
            api.update(assertNotNull(lockedSpec.version?.index),assertNotNull(lockedSpec.spec).copy(encryptionConfig=SwarmSpecEncryptionConfig(autoLockManagers=false))).getOrThrow()
        }
    }

    @Test fun workerJoinLeaveAndRemoval() = runTest(timeout=60.seconds) {
        withContext(Dispatchers.Default) {
            client().use { manager ->
                DockerClient(DockerClientConfig(connectionConfig= DockerClientConfig.ConnectionConfig.SocketConnection(
                    SWARM_SOCKET.substringBeforeLast('/')+"/worker.sock"))).use { worker ->
                    val swarm=manager.swarm.getInfo().getOrThrow()
                    val managerNode=manager.swarm.nodes.getList().getOrThrow().single()
                    worker.swarm.join(SwarmJoinRequest(listenAddr="0.0.0.0:2377",advertiseAddr="eth0",
                        remoteAddrs=listOf(assertNotNull(managerNode.status?.addr)+":2377"),
                        joinToken=assertNotNull(swarm.joinTokens?.worker))).getOrThrow()
                    try {
                        withTimeout(15000) {
                            while(manager.swarm.nodes.getList().getOrThrow().size<2) delay(100)
                        }
                        assertTrue(worker.swarm.services.getList().isError,"Worker must not act as manager")
                    } finally { worker.swarm.leave().getOrThrow() }
                    val workerNode=manager.swarm.nodes.getList().getOrThrow().single{it.ID!=managerNode.ID}
                    val id=assertNotNull(workerNode.ID)
                    manager.swarm.nodes.remove(id,force=true).getOrThrow()
                    assertTrue(manager.swarm.nodes.getInfo(id).isError)
                }
            }
        }
    }

    @Test fun serviceLifecycleTasksLogsAndRollback() = runTest(timeout=120.seconds) {
        withContext(Dispatchers.Default) {
            client().use { docker ->
                for(tty in listOf(false,true)) {
                    val api=docker.swarm
                    val name="kmp-service-${Random.nextInt(1,Int.MAX_VALUE)}"
                    val spec=ServiceSpec(name=name,labels=mapOf("sdk-test" to name),
                        taskTemplate=TaskSpec(containerSpec=TaskSpecContainerSpec(image="alpine:latest",TTY=tty,
                            command=listOf("sh","-c","echo swarm-out; echo swarm-err >&2; sleep 120")),
                            restartPolicy=TaskSpecRestartPolicy(condition=TaskSpecRestartPolicy.Condition.NONE),
                            logDriver=TaskSpecLogDriver(name="json-file")),
                        mode=ServiceSpecMode(replicated=ServiceSpecModeReplicated(replicas=1)))
                    val id=assertNotNull(api.services.create(spec).getOrThrow().ID)
                    try {
                        assertTrue(api.services.create(spec).isError)
                        assertEquals(listOf(id),api.services.getList(mapOf("label" to listOf("sdk-test=$name")),status=true).getOrThrow().map{it.ID})
                        val task=withTimeout(60_000) {
                            while(true) {
                                val tasks=api.tasks.getList(mapOf("service" to listOf(id))).getOrThrow()
                                val running=tasks.firstOrNull{it.status?.state==TaskState.RUNNING}
                                if(running!=null) return@withTimeout running
                                delay(250)
                            }
                            error("unreachable")
                        }
                        val taskId=assertNotNull(task.ID)
                        assertEquals(id,api.tasks.getInfo(taskId).getOrThrow().serviceID)
                        for(service in listOf(true,false)) {
                            val logs=withTimeout(15_000) {
                                (if(service) api.services.getLogs(id) else api.tasks.getLogs(taskId)).getOrThrow().toList()
                            }
                            assertTrue(logs.any{it.line.contains("swarm-out")})
                            assertTrue(logs.any{it.line.contains("swarm-err")})
                            if(!tty) assertTrue(logs.any{it.type==LogLine.Type.STDERR})
                        }
                        val found=api.services.getInfo(id,insertDefaults=true).getOrThrow()
                        val version=assertNotNull(found.version?.index)
                        val changed=assertNotNull(found.spec).copy(labels=mapOf("sdk-test" to name,"updated" to "yes"))
                        api.services.update(id,version,changed).getOrThrow()
                        assertTrue(api.services.update(id,version,changed).isError)
                        val updated=api.services.getInfo(id).getOrThrow()
                        assertEquals("yes",updated.spec?.labels?.get("updated"))
                        val rollback=api.services.update(id,assertNotNull(updated.version?.index),assertNotNull(updated.spec),rollback="previous")
                        assertFalse(rollback.isError, rollback.errorOrNull()?.message)
                        assertNull(api.services.getInfo(id).getOrThrow().spec?.labels?.get("updated"))
                    } finally { api.services.remove(id).getOrThrow() }
                    assertTrue(api.services.getInfo(id).isError)
                }
            }
        }
    }
}
