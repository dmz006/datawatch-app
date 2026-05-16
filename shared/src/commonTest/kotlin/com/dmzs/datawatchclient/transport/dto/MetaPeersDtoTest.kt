package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T18 test-debt: JSON deserialization for MetaPeersDto —
 * GET /api/federation/meta-peers (alpha.24 #231).
 */
class MetaPeersDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `meta peers response parses by-node buckets and primaries`() {
        val src = """
        {
          "self": "primary-host-1",
          "by_node": {
            "gpu-node-1": {
              "observers": [
                {
                  "primary": "primary-host-1",
                  "peer": "observer-a",
                  "shape": "standalone",
                  "last_push_at": "2026-05-01T10:00:00Z",
                  "version": "6.1.0"
                }
              ],
              "observer_count": 1,
              "primary_count": 1
            }
          },
          "unbound": [
            {
              "primary": "primary-host-1",
              "peer": "orphan-peer",
              "shape": "agent"
            }
          ],
          "primaries_walked": ["primary-host-1", "primary-host-2"]
        }
        """.trimIndent()

        val dto = json.decodeFromString(MetaPeersDto.serializer(), src)

        assertEquals("primary-host-1", dto.self)
        assertEquals(1, dto.byNode.size)

        val bucket = dto.byNode["gpu-node-1"] ?: error("gpu-node-1 missing")
        assertEquals(1, bucket.observers.size)
        assertEquals(1, bucket.observerCount)
        assertEquals(1, bucket.primaryCount)

        val observer = bucket.observers.first()
        assertEquals("primary-host-1", observer.primary)
        assertEquals("observer-a", observer.peer)
        assertEquals("standalone", observer.shape)
        assertEquals("6.1.0", observer.version)
        assertEquals("2026-05-01T10:00:00Z", observer.lastPushAt)

        assertEquals(1, dto.unbound.size)
        assertEquals("orphan-peer", dto.unbound.first().peer)
        assertEquals("agent", dto.unbound.first().shape)

        assertEquals(listOf("primary-host-1", "primary-host-2"), dto.primariesWalked)
    }

    @Test
    fun `empty meta peers response parses without error`() {
        val src = """{"self":"","by_node":{},"unbound":[],"primaries_walked":[]}"""
        val dto = json.decodeFromString(MetaPeersDto.serializer(), src)
        assertTrue(dto.byNode.isEmpty())
        assertTrue(dto.unbound.isEmpty())
        assertTrue(dto.primariesWalked.isEmpty())
    }

    @Test
    fun `encode-decode round-trip preserves self and primaries_walked`() {
        val original = MetaPeersDto(
            self = "host-a",
            byNode = emptyMap(),
            unbound = emptyList(),
            primariesWalked = listOf("host-a", "host-b"),
        )
        val encoded = json.encodeToString(MetaPeersDto.serializer(), original)
        val decoded = json.decodeFromString(MetaPeersDto.serializer(), encoded)
        assertEquals("host-a", decoded.self)
        assertEquals(listOf("host-a", "host-b"), decoded.primariesWalked)
    }

    @Test
    fun `meta observer entry null version is tolerated`() {
        val src = """
        {
          "self": "s",
          "by_node": {
            "n1": {
              "observers": [{"primary":"s","peer":"p","shape":"standalone"}],
              "observer_count": 1,
              "primary_count": 1
            }
          },
          "unbound": [],
          "primaries_walked": []
        }
        """.trimIndent()
        val dto = json.decodeFromString(MetaPeersDto.serializer(), src)
        val obs = dto.byNode["n1"]?.observers?.first() ?: error("missing observer")
        assertEquals(null, obs.version)
        assertEquals(null, obs.lastPushAt)
    }
}
