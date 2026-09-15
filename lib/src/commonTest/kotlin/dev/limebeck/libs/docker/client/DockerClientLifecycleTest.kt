package dev.limebeck.libs.docker.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

class DockerClientLifecycleTest {
    @Test
    fun useClosesClientOnNormalAndExceptionalExit() = runTest {
        for (fail in listOf(false, true)) {
            val docker = DockerClient()
            val failure = IllegalStateException("consumer failure")
            val outcome = runCatching {
                docker.use { if (fail) throw failure }
            }
            assertSame(if (fail) failure else null, outcome.exceptionOrNull())
            val job = requireNotNull(docker.client.coroutineContext[Job])
            job.join()
            assertFalse(job.isActive)
            docker.close()
        }
    }
}
