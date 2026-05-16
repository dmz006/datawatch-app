package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T18 test-debt: JSON round-trip for ObserverPeersByNodeDto —
 * GET /api/observer/peers/by-node (alpha.24 #231).
 */
class ObserverPeersByNodeDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `by-node response groups peers under compute-node keys`() {
        val src = """
        {
          "by_node": {
            "gpu-node-1": [
              {"name":"peer-a","shape":"standalone","version":"6.0.0"},
              {"name":"peer-b","shape":"agent"}
            ],
            "cpu-node-2": [
              {"name":"peer-c","shape":"standalone"}
            ]
          },
          "unbound": [
            {"name":"orphan-1","shape":"standalone"}
          ]
        }
        """.trimIndent()
        val dto = json.decodeFromString(ObserverPeersByNodeDto.serializer(), src)

        assertEquals(2, dto.byNode.size, "expected 2 compute-node buckets")
        val gpuPeers = dto.byNode["gpu-node-1"] ?: error("gpu-node-1 missing")
        assertEquals(2, gpuPeers.size)
        assertEquals("peer-a", gpuPeers[0].name)
        assertEquals("standalone", gpuPeers[0].shape)
        assertEquals("agent", gpuPeers[1].shape)

        val cpuPeers = dto.byNode["cpu-node-2"] ?: error("cpu-node-2 missing")
        assertEquals(1, cpuPeers.size)
        assertEquals("peer-c", cpuPeers[0].name)

        assertEquals(1, dto.unbound.size)
        assertEquals("orphan-1", dto.unbound[0].name)
    }

    @Test
    fun `empty response deserializes without error`() {
        val src = """{"by_node":{},"unbound":[]}"""
        val dto = json.decodeFromString(ObserverPeersByNodeDto.serializer(), src)
        assertTrue(dto.byNode.isEmpty())
        assertTrue(dto.unbound.isEmpty())
    }

    @Test
    fun `peer with compute_node field round-trips`() {
        val src = """
        {
          "by_node": {
            "node-x": [
              {"name":"bound-peer","shape":"standalone","compute_node":"node-x"}
            ]
          },
          "unbound": []
        }
        """.trimIndent()
        val dto = json.decodeFromString(ObserverPeersByNodeDto.serializer(), src)
        val peer = dto.byNode["node-x"]?.first() ?: error("missing peer")
        assertEquals("node-x", peer.computeNode)
    }

    @Test
    fun `encode-decode round-trip preserves all fields`() {
        val original = ObserverPeersByNodeDto(
            byNode = mapOf(
                "n1" to listOf(ObserverPeerDto(name = "p1", shape = "standalone")),
            ),
            unbound = listOf(ObserverPeerDto(name = "u1", shape = "agent")),
        )
        val encoded = json.encodeToString(ObserverPeersByNodeDto.serializer(), original)
        val decoded = json.decodeFromString(ObserverPeersByNodeDto.serializer(), encoded)
        assertEquals(original.byNode.keys, decoded.byNode.keys)
        assertEquals("p1", decoded.byNode["n1"]?.first()?.name)
        assertEquals("u1", decoded.unbound.first().name)
    }
}
