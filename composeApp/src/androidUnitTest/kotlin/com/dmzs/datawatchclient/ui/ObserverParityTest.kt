package com.dmzs.datawatchclient.ui

import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto
import com.dmzs.datawatchclient.ui.monitoring.FederatedPeersViewModel
import com.dmzs.datawatchclient.ui.monitoring.peerMatchesFilter
import com.dmzs.datawatchclient.ui.monitoring.snapshotEnvelopes
import com.dmzs.datawatchclient.ui.monitoring.snapshotHeaderLine
import com.dmzs.datawatchclient.ui.observer.parseChannelDiagnostics
import com.dmzs.datawatchclient.ui.sessions.summarizerEnabledIn
import com.dmzs.datawatchclient.ui.stats.StatsMetric
import com.dmzs.datawatchclient.ui.stats.StatsTone
import com.dmzs.datawatchclient.ui.stats.statsMetricTone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Parity D29a / D55a / D43a + Observer extras helpers. */
class ObserverParityTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `stats thresholds match the PWA verbatim (strict greater-than)`() {
        assertEquals(StatsTone.Success, statsMetricTone(StatsMetric.Cpu, 50.0))
        assertEquals(StatsTone.Warning, statsMetricTone(StatsMetric.Cpu, 50.1))
        assertEquals(StatsTone.Warning, statsMetricTone(StatsMetric.Cpu, 80.0))
        assertEquals(StatsTone.Error, statsMetricTone(StatsMetric.Cpu, 80.1))
        assertEquals(StatsTone.Accent, statsMetricTone(StatsMetric.Memory, 85.0))
        assertEquals(StatsTone.Error, statsMetricTone(StatsMetric.Memory, 85.5))
        assertEquals(StatsTone.Accent2, statsMetricTone(StatsMetric.Disk, 90.0))
        assertEquals(StatsTone.Error, statsMetricTone(StatsMetric.Disk, 91.0))
        assertEquals(StatsTone.Success, statsMetricTone(StatsMetric.Gpu, 80.0))
        assertEquals(StatsTone.Error, statsMetricTone(StatsMetric.Gpu, 81.0))
    }

    @Test
    fun `channel diagnostics parse sessions and hints`() {
        val d =
            parseChannelDiagnostics(
                obj(
                    """{"sessions":[{"session_id":"h-1","session_name":"build","channel_port":7433,"bridge_alive":true},
                       {"session_id":"h-2","channel_port":0,"bridge_alive":false,"probe_error":"refused"}],
                       "hints":["restart the bridge"]}""",
                ),
            )
        assertEquals(2, d.rows.size)
        assertEquals("build", d.rows[0].name)
        assertTrue(d.rows[0].alive)
        assertEquals("h-2", d.rows[1].name)
        assertEquals(0, d.rows[1].port)
        assertEquals("refused", d.rows[1].error)
        assertEquals(listOf("restart the bridge"), d.hints)
    }

    @Test
    fun `peer snapshot sorts envelopes by cpu and builds the header`() {
        val snap =
            obj(
                """{"host":{"name":"gpu-box","os":"linux","arch":"amd64","uptime_seconds":42},
                   "envelopes":[{"kind":"session","id":"a","cpu_pct":1.5,"rss_bytes":2000000,"process_count":3,"open_fds":9},
                                {"kind":"llm","id":"b","cpu_pct":40.0,"rss_bytes":5000000}]}""",
            )
        val envs = snapshotEnvelopes(snap)
        assertEquals(listOf("b", "a"), envs.map { it.id })
        assertEquals(5L, envs[0].rssMb)
        assertEquals(3, envs[1].procs)
        assertEquals("peer1 · gpu-box · linux amd64 · uptime 42s", snapshotHeaderLine("peer1", snap))
    }

    @Test
    fun `peer filter pills include agent host shapes`() {
        val agent = ObserverPeerDto(name = "x", shape = "agent")
        val standalone = ObserverPeerDto(name = "y", shape = "standalone")
        assertTrue(peerMatchesFilter(agent, FederatedPeersViewModel.Filter.Agent))
        assertFalse(peerMatchesFilter(standalone, FederatedPeersViewModel.Filter.Agent))
        assertTrue(peerMatchesFilter(standalone, FederatedPeersViewModel.Filter.All))
    }

    @Test
    fun `summarizer flag reads nested session summarizer enabled`() {
        assertTrue(summarizerEnabledIn(obj("""{"session":{"summarizer":{"enabled":true}}}""")))
        assertFalse(summarizerEnabledIn(obj("""{"session":{"summarizer":{"enabled":false}}}""")))
        assertFalse(summarizerEnabledIn(obj("""{}""")))
    }
}
