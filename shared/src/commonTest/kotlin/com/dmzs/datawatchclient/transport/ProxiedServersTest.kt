package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxiedServersTest {
    private fun real(
        id: String,
        name: String = id,
        base: String = "https://$id.example:8443",
        enabled: Boolean = true,
        pin: String? = null,
    ) = ServerProfile(
        id = id,
        displayName = name,
        baseUrl = base,
        bearerTokenRef = "alias-$id",
        trustAnchorSha256 = pin,
        reachabilityProfileId = "lan",
        enabled = enabled,
        createdTs = 1L,
    )

    private fun remote(
        name: String,
        label: String? = null,
        url: String? = "https://$name.remote.example",
        enabled: Boolean? = true,
        builtin: Boolean = false,
    ): JsonObject =
        JsonObject(
            buildMap {
                put("name", JsonPrimitive(name))
                label?.let { put("label", JsonPrimitive(it)) }
                url?.let { put("url", JsonPrimitive(it)) }
                enabled?.let { put("enabled", JsonPrimitive(it)) }
                if (builtin) put("builtin", JsonPrimitive(true))
            },
        )

    @Test
    fun `make builds proxy base url - id - display name and reuses parent token ref and pin`() {
        val parent = real("ws1", name = "workstation", base = "https://ws1.example:8443/", pin = "AB:CD")
        val v = ProxiedServers.make(parent, "demo", null)
        assertEquals("ws1::proxy::demo", v.id)
        assertEquals("workstation › demo", v.displayName)
        assertEquals("https://ws1.example:8443/api/proxy/demo", v.baseUrl)
        assertEquals("alias-ws1", v.bearerTokenRef)
        assertEquals("AB:CD", v.trustAnchorSha256)
        assertTrue(v.enabled)
        assertEquals("https://ws1.example:8443", ProxiedServers.docsBaseUrl(v))
        assertEquals("https://ws1.example:8443/", ProxiedServers.docsBaseUrl(parent))
    }

    @Test
    fun `label wins over name in display name and names are path-encoded`() {
        val parent = real("ws1", name = "workstation")
        val v = ProxiedServers.make(parent, "my box", "Lab Box")
        assertEquals("workstation › Lab Box", v.displayName)
        assertEquals("https://ws1.example:8443/api/proxy/my%20box", v.baseUrl)
        assertEquals("ws1::proxy::my box", v.id)
    }

    @Test
    fun `id helpers round-trip`() {
        val id = ProxiedServers.idFor("p-1", "demo")
        assertTrue(ProxiedServers.isProxied(id))
        assertFalse(ProxiedServers.isProxied("p-1"))
        assertFalse(ProxiedServers.isProxied(null))
        assertEquals("p-1", ProxiedServers.parentIdOf(id))
        assertEquals("p-1", ProxiedServers.parentIdOf("p-1"))
        assertEquals("demo", ProxiedServers.remoteNameOf(id))
        assertNull(ProxiedServers.remoteNameOf("p-1"))
        assertEquals("p-1", ProxiedServers.realIdOf(id))
        assertNull(ProxiedServers.realIdOf(null))
    }

    @Test
    fun `parse skips disabled - local - reserved - nameless - self and duplicate entries but keeps builtin`() {
        val parent = real("ws1", base = "https://ws1.example:8443")
        val list =
            listOf(
                remote("local"),
                remote("demo"),
                remote("off", enabled = false),
                remote("yaml", builtin = true),
                remote("agent"),
                remote("self", url = "https://ws1.example:8443/"),
                remote("demo"),
                JsonObject(mapOf("url" to JsonPrimitive("https://x"))),
                remote("noflag", enabled = null),
            )
        val out = ProxiedServers.parse(parent, list)
        assertEquals(listOf("demo", "yaml", "noflag"), out.map { ProxiedServers.remoteNameOf(it.id) })
    }

    @Test
    fun `withProxied appends virtual profiles of enabled parents only`() {
        val a = real("a")
        val b = real("b", enabled = false)
        val byParent =
            mapOf(
                "a" to listOf(ProxiedServers.make(a, "x", null)),
                "b" to listOf(ProxiedServers.make(b, "y", null)),
            )
        val out = ProxiedServers.withProxied(listOf(a, b), byParent, null)
        assertEquals(listOf("a", "b", "a::proxy::x"), out.map { it.id })
    }

    @Test
    fun `withProxied synthesizes the active virtual profile before the first fetch`() {
        val a = real("a", name = "A")
        val out = ProxiedServers.withProxied(listOf(a), emptyMap(), "a::proxy::demo")
        assertEquals(listOf("a", "a::proxy::demo"), out.map { it.id })
        assertEquals("A › demo", out.last().displayName)
        // Once the parent was fetched and the remote is gone, no synthesis.
        val fetched = ProxiedServers.withProxied(listOf(a), mapOf("a" to emptyList()), "a::proxy::demo")
        assertEquals(listOf("a"), fetched.map { it.id })
    }

    @Test
    fun `resolveActive handles real - virtual - vanished remote and missing parent`() {
        val a = real("a")
        val b = real("b")
        val vx = ProxiedServers.make(b, "x", "X")
        val byParent = mapOf("b" to listOf(vx))
        val reals = listOf(a, b)
        assertEquals("a", ProxiedServers.resolveActive(reals, byParent, null)?.id)
        assertEquals("b", ProxiedServers.resolveActive(reals, byParent, "b")?.id)
        assertEquals("a", ProxiedServers.resolveActive(reals, byParent, "gone")?.id)
        assertEquals(vx, ProxiedServers.resolveActive(reals, byParent, "b::proxy::x"))
        // remote vanished → parent, not first profile
        assertEquals("b", ProxiedServers.resolveActive(reals, byParent, "b::proxy::zzz")?.id)
        // parent gone → first enabled
        assertEquals("a", ProxiedServers.resolveActive(reals, byParent, "c::proxy::x")?.id)
        assertNull(ProxiedServers.resolveActive(emptyList(), byParent, "b::proxy::x"))
    }

    @Test
    fun `repairActiveId moves vanished remote to parent and clears orphaned selection`() {
        val b = real("b")
        val byParent = mapOf("b" to listOf(ProxiedServers.make(b, "x", null)))
        assertNull(ProxiedServers.repairActiveId(listOf(b), byParent, "b"))
        assertNull(ProxiedServers.repairActiveId(listOf(b), byParent, null))
        assertNull(ProxiedServers.repairActiveId(listOf(b), byParent, "b::proxy::x"))
        assertNull(ProxiedServers.repairActiveId(listOf(b), emptyMap(), "b::proxy::y"))
        assertEquals(ProxiedServers.Repair("b"), ProxiedServers.repairActiveId(listOf(b), byParent, "b::proxy::y"))
        assertEquals(ProxiedServers.Repair(null), ProxiedServers.repairActiveId(emptyList(), byParent, "b::proxy::x"))
    }

    @Test
    fun `groupedForPicker puts each remote right after its parent and chip label shortens with one server`() {
        val a = real("a", name = "A")
        val b = real("b", name = "B")
        val bx = ProxiedServers.make(b, "x", null)
        val ay = ProxiedServers.make(a, "y", null)
        val grouped = ProxiedServers.groupedForPicker(listOf(a, b), listOf(bx, ay))
        assertEquals(listOf("a", "a::proxy::y", "b", "b::proxy::x"), grouped.map { it.id })
        assertEquals("y", ProxiedServers.chipLabel(ay, 1))
        assertEquals("A › y", ProxiedServers.chipLabel(ay, 2))
        assertEquals("A", ProxiedServers.chipLabel(a, 1))
    }

    @Test
    fun `registry keeps last good list on failure and drops removed parents`() =
        runTest {
            val a = real("a")
            val b = real("b")
            var failA = false
            val reg =
                ProxiedServersRegistry { p ->
                    if (p.id == "a" && failA) {
                        Result.failure(IllegalStateException("down"))
                    } else {
                        Result.success(listOf(remote("r-${p.id}")))
                    }
                }
            reg.refresh(listOf(a, b, ProxiedServers.make(a, "ignored", null)))
            assertEquals(setOf("a::proxy::r-a", "b::proxy::r-b"), reg.virtualProfiles().map { it.id }.toSet())
            assertEquals("a::proxy::r-a", reg.find("a::proxy::r-a")?.id)
            failA = true
            reg.refresh(listOf(a))
            assertEquals(listOf("a::proxy::r-a"), reg.virtualProfiles().map { it.id })
            assertNull(reg.find("b::proxy::r-b"))
        }

    @Test
    fun `registry reports failures with a capped backoff and resets on success`() =
        runTest {
            val a = real("a")
            var fail = true
            val reg =
                ProxiedServersRegistry {
                    if (fail) Result.failure(IllegalStateException("down")) else Result.success(listOf(remote("r")))
                }
            assertNull(reg.retryDelayMs())
            assertFalse(reg.refresh(listOf(a)))
            assertEquals(2_000L, reg.retryDelayMs())
            assertFalse(reg.refresh(listOf(a)))
            assertEquals(4_000L, reg.retryDelayMs())
            repeat(10) { reg.refresh(listOf(a)) }
            assertEquals(60_000L, reg.retryDelayMs(), "capped at 60 s")
            fail = false
            assertTrue(reg.refresh(listOf(a)))
            assertNull(reg.retryDelayMs())
            assertEquals(listOf("a::proxy::r"), reg.virtualProfiles().map { it.id })
        }

    @Test
    fun `a server without api servers counts as no remotes - not a failure`() =
        runTest {
            val reg = ProxiedServersRegistry { Result.failure(TransportError.NotFound("404")) }
            assertTrue(reg.refresh(listOf(real("a"))))
            assertEquals(emptyList(), reg.virtualProfiles())
            assertEquals(setOf("a"), reg.byParent.value.keys)
        }

    @Test
    fun `first load is pending only while a refresh runs for a never-listed parent`() =
        runTest {
            val a = real("a")
            val b = real("b")
            val reg = ProxiedServersRegistry { Result.success(emptyList()) }
            assertFalse(reg.firstLoadPending(listOf(a), inFlight = false, byParent = emptyMap()))
            assertTrue(reg.firstLoadPending(listOf(a), inFlight = true, byParent = emptyMap()))
            assertFalse(reg.firstLoadPending(listOf(a), inFlight = true, byParent = mapOf("a" to emptyList())))
            assertTrue(reg.firstLoadPending(listOf(a, b), inFlight = true, byParent = mapOf("a" to emptyList())))
            assertFalse(
                reg.firstLoadPending(listOf(a, real("c", enabled = false)), inFlight = true, byParent = mapOf("a" to emptyList())),
            )
            reg.refresh(listOf(a))
            assertFalse(reg.inFlight.value)
        }

    @Test
    fun `backoff doubles from the base and caps`() {
        assertEquals(2_000L, ProxiedServers.backoffMs(0))
        assertEquals(2_000L, ProxiedServers.backoffMs(1))
        assertEquals(4_000L, ProxiedServers.backoffMs(2))
        assertEquals(32_000L, ProxiedServers.backoffMs(5))
        assertEquals(60_000L, ProxiedServers.backoffMs(6))
        assertEquals(60_000L, ProxiedServers.backoffMs(500))
        assertEquals(5_000L, ProxiedServers.backoffMs(1, baseMs = 5_000L))
        assertEquals(40_000L, ProxiedServers.backoffMs(4, baseMs = 5_000L))
    }
}
