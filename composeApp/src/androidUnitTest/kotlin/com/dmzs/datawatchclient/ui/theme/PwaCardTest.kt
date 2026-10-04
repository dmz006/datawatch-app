package com.dmzs.datawatchclient.ui.theme

import android.content.Context
import com.dmzs.datawatchclient.prefs.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Parity D26a (docs slug) + D27a (persisted collapse state). */
class PwaCardTest {
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var ctx: Context

    @BeforeTest
    fun setUp() {
        PwaCardCollapseStore.resetForTest()
        prefs = FakeSharedPreferences()
        ctx = mockk()
        every { ctx.applicationContext } returns ctx
        every { ctx.getSharedPreferences(any(), any()) } returns prefs
    }

    @AfterTest
    fun tearDown() = PwaCardCollapseStore.resetForTest()

    @Test
    fun `slug matches PWA defsLink`() {
        assertEquals("scheduled-events", pwaDocsSlug("Scheduled Events"))
        assertEquals("cost-rates-usd-1k-tokens", pwaDocsSlug("Cost Rates (USD / 1K tokens)"))
        assertEquals("automata-dag-orchestrator", pwaDocsSlug("Automata-DAG orchestrator"))
        assertEquals("llms", pwaDocsSlug("  LLMs!! "))
    }

    @Test
    fun `cards default to expanded like the PWA`() {
        assertFalse(PwaCardCollapseStore.isCollapsed(ctx, "schedules"))
    }

    @Test
    fun `toggle persists per card id`() {
        PwaCardCollapseStore.toggle(ctx, "schedules")
        assertTrue(PwaCardCollapseStore.isCollapsed(ctx, "schedules"))
        assertFalse(PwaCardCollapseStore.isCollapsed(ctx, "cooldown"))
        assertEquals(setOf("schedules"), prefs.getStringSet(PwaCardCollapseStore.KEY, null))

        // A fresh process reloads the persisted set.
        PwaCardCollapseStore.resetForTest()
        assertTrue(PwaCardCollapseStore.isCollapsed(ctx, "schedules"))

        PwaCardCollapseStore.toggle(ctx, "schedules")
        assertFalse(PwaCardCollapseStore.isCollapsed(ctx, "schedules"))
        assertEquals(emptySet(), prefs.getStringSet(PwaCardCollapseStore.KEY, null))
    }
}
