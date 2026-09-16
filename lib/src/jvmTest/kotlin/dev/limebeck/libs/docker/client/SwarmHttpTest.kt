package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.api.swarm
import dev.limebeck.libs.docker.client.model.*
import dev.limebeck.libs.docker.client.diagnostics.dockerContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLDecoder
import kotlin.test.*

class SwarmHttpTest {
    private fun client(daemon: MockDockerDaemon) = DockerClient(DockerClientConfig(
        connectionConfig = DockerClientConfig.ConnectionConfig.SocketConnection(daemon.path.toString())))

    @Test fun clusterRequestsAndSensitiveBodies() = runBlocking {
        MockDockerDaemon(listOf(DockerReply("\"node-id\""), DockerReply("{}"), DockerReply(),
            DockerReply("{\"UnlockKey\":\"key\"}"), DockerReply(), DockerReply(), DockerReply())).use { daemon ->
            client(daemon).use { docker ->
                val api = docker.swarm
                assertEquals("node-id", api.init(SwarmInitRequest(advertiseAddr="eth0")).getOrThrow())
                api.getInfo().getOrThrow()
                api.update(ULong.MAX_VALUE, SwarmSpec(name="cluster"), true, true, true).getOrThrow()
                assertEquals("key", api.getUnlockKey().getOrThrow().unlockKey)
                api.unlock(SwarmUnlockRequest(unlockKey="key")).getOrThrow()
                api.leave().getOrThrow()
                api.join(SwarmJoinRequest(joinToken="token", remoteAddrs=listOf("manager:2377"))).getOrThrow()
                val paths = daemon.requests.map { URI(it.line.split(' ')[1]) }
                assertEquals(listOf("init", "", "update", "unlockkey", "unlock", "leave", "join"), paths.map { it.path.removePrefix("/v1.51/swarm").removePrefix("/") })
                assertTrue(paths[2].query.contains("version=18446744073709551615"))
                for (key in listOf("rotateWorkerToken", "rotateManagerToken", "rotateManagerUnlockKey")) assertTrue(paths[2].query.contains("$key=true"))
                assertEquals("force=false", paths[5].query)
                assertEquals("key", Json.parseToJsonElement(daemon.requests[4].body).jsonObject["UnlockKey"]?.jsonPrimitive?.content)
                assertEquals("token", Json.parseToJsonElement(daemon.requests[6].body).jsonObject["JoinToken"]?.jsonPrimitive?.content)
            }
            daemon.checkHealthy()
        }
    }

    @Test fun nodeServiceAndTaskRequestOptions() = runBlocking {
        MockDockerDaemon(listOf(DockerReply("[]"), DockerReply("{}"), DockerReply(), DockerReply(),
            DockerReply("[]"), DockerReply("{\"ID\":\"service\",\"Warnings\":[\"warning\"]}", "201 Created"),
            DockerReply("{}"), DockerReply("{\"Warnings\":[\"updated\"]}"), DockerReply(), DockerReply("[]"), DockerReply("{}"))).use { daemon ->
            client(daemon).use { docker ->
                val filters = mapOf("label" to listOf("x=a+b & c"))
                val api = docker.swarm
                api.nodes.getList(filters).getOrThrow()
                api.nodes.getInfo("node ?#").getOrThrow()
                api.nodes.update("node", ULong.MAX_VALUE, NodeSpec(labels=mapOf("a" to "b"))).getOrThrow()
                api.nodes.remove("node", force=true).getOrThrow()
                api.services.getList(filters, status=true).getOrThrow()
                assertEquals(listOf("warning"), api.services.create(ServiceSpec(name="service"), "encoded-auth").getOrThrow().warnings)
                api.services.getInfo("service ?#", insertDefaults=true).getOrThrow()
                assertEquals(listOf("updated"), api.services.update("service", ULong.MAX_VALUE, ServiceSpec(), "encoded-auth", "previous-spec", "previous").getOrThrow().warnings)
                api.services.remove("service").getOrThrow()
                api.tasks.getList(filters).getOrThrow()
                api.tasks.getInfo("task ?#").getOrThrow()
                val uris = daemon.requests.map { URI(it.line.split(' ')[1]) }
                for (i in listOf(0,4,9)) assertTrue(URLDecoder.decode(uris[i].rawQuery, "UTF-8").contains(Json.encodeToString(filters)))
                assertEquals("/v1.51/nodes/node ?#", uris[1].path)
                assertNull(uris[1].query)
                assertEquals("force=true", uris[3].query)
                assertTrue(uris[4].query.contains("status=true"))
                assertEquals("insertDefaults=true", uris[6].query)
                assertTrue(uris[7].query.contains("version=18446744073709551615"))
                assertTrue(uris[7].query.contains("registryAuthFrom=previous-spec"))
                assertTrue(uris[7].query.contains("rollback=previous"))
                for (i in listOf(5,7)) assertEquals("encoded-auth", daemon.requests[i].headers["x-registry-auth"])
                assertEquals("/v1.51/tasks/task ?#", uris[10].path)
                assertEquals(listOf("GET","GET","POST","DELETE","GET","POST","GET","POST","DELETE","GET","GET"), daemon.requests.map { it.line.substringBefore(' ') })
            }
            daemon.checkHealthy()
        }
    }

    private fun inspect(service: Boolean, tty: Boolean) = if(service)
        "{\"Spec\":{\"TaskTemplate\":{\"ContainerSpec\":{\"TTY\":$tty}}}}"
        else "{\"Spec\":{\"ContainerSpec\":{\"TTY\":$tty}}}"

    @Test fun logsAreColdAndDecodeBothFramings() = runBlocking {
        for (service in listOf(true,false)) for (tty in listOf(true,false)) {
            val framed = "\u0001\u0000\u0000\u0000\u0000\u0000\u0000\u0004out\n\u0002\u0000\u0000\u0000\u0000\u0000\u0000\u0004err\n"
            MockDockerDaemon(listOf(DockerReply(inspect(service,tty)), DockerReply(if(tty) "out\nerr\n" else framed))).use { daemon ->
                client(daemon).use { docker ->
                    val parameters=SwarmLogsParameters(details=true, timestamps=true, since=123, tail="2")
                    val flow = (if(service) docker.swarm.services.getLogs("id",parameters) else docker.swarm.tasks.getLogs("id",parameters)).getOrThrow()
                    assertEquals(1,daemon.requests.size)
                    val lines=flow.toList()
                    assertEquals(2, lines.size)
                    assertTrue(lines[0].line.contains("out"))
                    assertTrue(lines[1].line.contains("err"))
                    if(!tty) assertEquals(listOf(LogLine.Type.STDOUT,LogLine.Type.STDERR),lines.map{it.type})
                    val query=URI(daemon.requests[1].line.split(' ')[1]).query
                    for (part in listOf("details=true","timestamps=true","since=123","tail=2","stdout=true","stderr=true","follow=false")) assertTrue(query.contains(part))
                }
                daemon.checkHealthy()
            }
        }
    }

    @Test fun logErrorsHaveSafeContextAndCancellationClosesStreams() = runBlocking {
        for(service in listOf(true,false)) {
            MockDockerDaemon(listOf(DockerReply(inspect(service,false)), DockerReply("{\"message\":\"denied\"}","503 Service Unavailable"))).use { daemon ->
                client(daemon).use { docker ->
                    val flow=(if(service) docker.swarm.services.getLogs("private") else docker.swarm.tasks.getLogs("private")).getOrThrow()
                    val error=assertFails { flow.toList() }
                    assertEquals(503,error.dockerContext?.httpStatus)
                    assertEquals("/${if(service) "services" else "tasks"}/{resource}/logs",error.dockerContext?.route)
                }
                daemon.checkHealthy()
            }
            MockDockerDaemon(listOf(DockerReply(inspect(service,false)), DockerReply(sendResponse=false))).use { daemon ->
                client(daemon).use { docker ->
                    val flow=(if(service) docker.swarm.services.getLogs("id") else docker.swarm.tasks.getLogs("id")).getOrThrow()
                    val job=launch { flow.collect() }
                    withTimeout(5000) { while(daemon.requests.size<2) delay(10) }
                    job.cancelAndJoin()
                    withTimeout(5000) { while(!daemon.peerClosed.get()) delay(10) }
                }
                daemon.checkHealthy()
            }
        }
    }
}
