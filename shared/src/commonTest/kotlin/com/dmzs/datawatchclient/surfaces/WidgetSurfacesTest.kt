package com.dmzs.datawatchclient.surfaces

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * iOS widget / Siri logic ported from Android's SessionsWidget, MonitorWidget,
 * WidgetActions and VoiceCommandActivity (BL403). Each test names the Android
 * behaviour it pins.
 */
class WidgetSurfacesTest {
    private fun profile(
        id: String,
        enabled: Boolean = true,
    ) = ServerProfile(
        id = id,
        displayName = "srv-$id",
        baseUrl = "https://$id.example:8443",
        bearerTokenRef = "dw.profile.$id",
        reachabilityProfileId = "",
        enabled = enabled,
        createdTs = 0,
    )

    private fun session(
        id: String,
        state: SessionState,
        lastMs: Long,
        name: String? = null,
    ) = Session(
        id = id,
        serverProfileId = "a",
        state = state,
        createdAt = Instant.fromEpochMilliseconds(0),
        lastActivityAt = Instant.fromEpochMilliseconds(lastMs),
        name = name,
    )

    // ── WidgetConfig ──

    @Test
    fun `config drops disabled servers and keeps token alias only`() {
        val cfg = WidgetConfig.from(listOf(profile("a"), profile("b", enabled = false), profile("c")), "c")
        assertEquals(listOf("a", "c"), cfg.servers.map { it.id })
        assertEquals("dw.profile.a", cfg.servers[0].bearerTokenRef)
        assertEquals("c", cfg.pick()?.id)
    }

    @Test
    fun `pick falls back to first enabled when active is missing disabled or all-servers`() {
        val profiles = listOf(profile("a"), profile("b", enabled = false))
        assertEquals("a", WidgetConfig.from(profiles, "b").pick()?.id)
        assertEquals("a", WidgetConfig.from(profiles, "__all__").pick()?.id)
        assertEquals("a", WidgetConfig.from(profiles, null).pick()?.id)
        assertNull(WidgetConfig.from(emptyList(), "a").pick())
    }

    @Test
    fun `cycle advances and wraps like WidgetActions cycleActiveServer`() {
        val cfg = WidgetConfig.from(listOf(profile("a"), profile("b"), profile("c")), "b")
        assertEquals("c", cfg.cycled().activeId)
        assertEquals("a", cfg.cycled().cycled().activeId)
        // Unknown / all-servers active id → first server.
        assertEquals("a", cfg.copy(activeId = "__all__").cycled().activeId)
    }

    @Test
    fun `cycle is a no-op with one server`() {
        val cfg = WidgetConfig.from(listOf(profile("a")), "a")
        assertEquals(cfg, cfg.cycled())
    }

    @Test
    fun `config round-trips through json and rejects garbage`() {
        val cfg = WidgetConfig.from(listOf(profile("a"), profile("b")), "b")
        assertEquals(cfg, WidgetConfig.decode(cfg.encode()))
        assertNull(WidgetConfig.decode("not json"))
        assertNull(WidgetConfig.decode(null))
    }

    @Test
    fun `widget server maps back to a profile for the transport`() {
        val p = WidgetConfig.from(listOf(profile("a")), "a").servers[0].toProfile()
        assertEquals("https://a.example:8443", p.baseUrl)
        assertEquals("dw.profile.a", p.bearerTokenRef)
        assertTrue(p.enabled)
    }

    // ── Session counts ──

    @Test
    fun `counts running waiting and total like SessionsWidget`() {
        val c =
            WidgetSessionCounts.of(
                listOf(
                    session("1", SessionState.Running, 1),
                    session("2", SessionState.Waiting, 2),
                    session("3", SessionState.Waiting, 3),
                    session("4", SessionState.Completed, 4),
                ),
            )
        assertEquals(WidgetSessionCounts(running = 1, waiting = 2, total = 4), c)
    }

    // ── Monitor snapshot ──

    @Test
    fun `cpu uses load over cores when both known`() {
        val m = WidgetMonitorSnapshot.of(StatsDto(cpuLoad1 = 2.0, cpuCores = 8, cpuPct = 99.0))
        assertEquals(25, m.cpuPct)
        assertEquals("2.00", m.cpuText)
    }

    @Test
    fun `cpu falls back to flat percent and dash`() {
        val m = WidgetMonitorSnapshot.of(StatsDto(cpuPct = 12.34))
        assertEquals(12, m.cpuPct)
        assertEquals("12.3%", m.cpuText)
        val none = WidgetMonitorSnapshot.of(StatsDto())
        assertEquals(-1, none.cpuPct)
        assertEquals("—", none.cpuText)
    }

    @Test
    fun `memory and disk use decimal units`() {
        val m =
            WidgetMonitorSnapshot.of(
                StatsDto(memUsed = 4_000_000_000, memTotal = 16_000_000_000, diskPct = 42.6),
            )
        assertEquals(25, m.memPct)
        assertEquals("4.0 GB / 16.0 GB", m.memText)
        assertEquals(42, m.diskPct)
        assertEquals("43%", m.diskText)
    }

    @Test
    fun `swap row only when the host has swap`() {
        assertFalse(WidgetMonitorSnapshot.of(StatsDto()).hasSwap)
        val m = WidgetMonitorSnapshot.of(StatsDto(swapUsed = 500_000_000, swapTotal = 2_000_000_000))
        assertTrue(m.hasSwap)
        assertEquals(25, m.swapPct)
        assertEquals("500 MB / 2.0 GB", m.swapText)
    }

    @Test
    fun `gpu shows util temp and vram with the vram bar`() {
        val m =
            WidgetMonitorSnapshot.of(
                StatsDto(gpuName = "RTX", gpuUtilPct = 37.0, gpuTemp = 61.7, gpuMemUsedMb = 2048, gpuMemTotalMb = 8192),
            )
        assertTrue(m.hasGpu)
        assertEquals(25, m.gpuPct)
        assertEquals("37% · 61°C · 2048/8192M", m.gpuText)
        assertFalse(WidgetMonitorSnapshot.of(StatsDto()).hasGpu)
    }

    @Test
    fun `footer texts match MonitorWidget`() {
        val m =
            WidgetMonitorSnapshot.of(
                StatsDto(
                    sessionsTotal = 5,
                    sessionsRunning = 2,
                    sessionsWaiting = 1,
                    uptimeSeconds = 90_061,
                    daemonRssBytes = 52_000_000,
                    goroutines = 40,
                    openFds = 12,
                    netRxBytes = 1_500,
                    netTxBytes = 999,
                    ebpfActive = true,
                ),
            )
        assertEquals("5 · 2r · 1w", m.sessionsText)
        assertEquals("1d1h", m.uptimeText)
        assertEquals("52 MB RSS · 40g · 12fd", m.daemonText)
        assertEquals("↓ 2 KB", m.netRxText)
        assertEquals("↑ 999 B", m.netTxText)
        assertTrue(m.ebpfActive)
    }

    @Test
    fun `uptime short forms`() {
        assertEquals("5m", WidgetMonitorSnapshot.uptimeShort(300))
        assertEquals("2h3m", WidgetMonitorSnapshot.uptimeShort(7_380))
    }

    @Test
    fun `fixed rounds half up`() {
        assertEquals("0.13", WidgetMonitorSnapshot.fixed(0.125, 2))
        assertEquals("3", WidgetMonitorSnapshot.fixed(2.5, 0))
        assertEquals("-1.5", WidgetMonitorSnapshot.fixed(-1.5, 1))
    }

    // ── Siri send target ──

    @Test
    fun `send target is the most recently active running or waiting session`() {
        val list =
            listOf(
                session("old", SessionState.Running, 10),
                session("new", SessionState.Waiting, 30),
                session("done", SessionState.Completed, 99),
            )
        assertEquals("new", VoiceSendTarget.resolve(list, null)?.id)
        assertEquals("new", VoiceSendTarget.resolve(list, " ")?.id)
    }

    @Test
    fun `send target matches the hint by name or id prefix, else newest`() {
        val list =
            listOf(
                session("ab12", SessionState.Running, 10, name = "Build Server"),
                session("cd34", SessionState.Waiting, 30),
            )
        assertEquals("ab12", VoiceSendTarget.resolve(list, "build")?.id)
        assertEquals("cd34", VoiceSendTarget.resolve(list, "CD")?.id)
        assertEquals("cd34", VoiceSendTarget.resolve(list, "nothing")?.id)
    }

    @Test
    fun `no send target without running sessions`() {
        assertNull(VoiceSendTarget.resolve(listOf(session("x", SessionState.Killed, 1)), null))
    }
}
