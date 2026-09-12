package dev.limebeck.libs.docker.client

import dev.limebeck.libs.docker.client.model.ContainerStatsResponse
import dev.limebeck.libs.docker.client.model.ContainerNetworkStats
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.*

class StatsCounterTest {
    @Test fun unsignedCountersRetainTheirFullRangeOnEveryPlatform() {
        val stats = DockerClientConfig().json.decodeFromString<ContainerStatsResponse>("""
            {
              "cpu_stats": {
                "system_cpu_usage": 18446744073709551615,
                "online_cpus": 4294967295,
                "cpu_usage": {"total_usage": 9007199254740993}
              },
              "memory_stats": {"usage": 18446744073709551615},
              "networks": {"eth0": {"rx_bytes": 9007199254740993}},
              "pids_stats": {"limit": 18446744073709551615}
            }
        """.trimIndent())
        assertEquals(ULong.MAX_VALUE, stats.cpuStats?.systemCpuUsage)
        assertEquals(UInt.MAX_VALUE, stats.cpuStats?.onlineCpus)
        assertEquals(9007199254740993uL, stats.cpuStats?.cpuUsage?.totalUsage)
        assertEquals(ULong.MAX_VALUE, stats.memoryStats?.usage)
        val network = DockerClientConfig().json.decodeFromJsonElement<ContainerNetworkStats>(stats.networks!!.getValue("eth0"))
        assertEquals(9007199254740993uL, network.rxBytes)
        assertEquals(ULong.MAX_VALUE, stats.pidsStats?.limit)
    }
}
