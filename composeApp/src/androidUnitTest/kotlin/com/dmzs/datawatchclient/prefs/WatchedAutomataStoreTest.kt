package com.dmzs.datawatchclient.prefs

import android.content.Context
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchedAutomataStoreTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var store: WatchedAutomataStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val fakePrefs = FakeSharedPreferences()
        val ctx = mockk<Context>()
        every { ctx.getSharedPreferences(any(), any()) } returns fakePrefs
        store = WatchedAutomataStore(ctx)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `watchedIds_emptyByDefault`() {
        assertTrue(store.watchedIds("p1").isEmpty())
    }

    @Test
    fun `setWatched_true_addsToSet`() {
        store.setWatched("p1", "prd-1", true)
        assertTrue(store.isWatched("p1", "prd-1"))
        assertEquals(setOf("prd-1"), store.watchedIds("p1"))
    }

    @Test
    fun `setWatched_false_removesFromSet`() {
        store.setWatched("p1", "prd-1", true)
        store.setWatched("p1", "prd-1", false)
        assertFalse(store.isWatched("p1", "prd-1"))
        assertTrue(store.watchedIds("p1").isEmpty())
    }

    @Test
    fun `profileIsolation_separateSetsPerProfile`() {
        store.setWatched("p1", "prd-A", true)
        store.setWatched("p2", "prd-B", true)

        assertTrue(store.isWatched("p1", "prd-A"))
        assertFalse(store.isWatched("p1", "prd-B"))
        assertFalse(store.isWatched("p2", "prd-A"))
        assertTrue(store.isWatched("p2", "prd-B"))
    }

    @Test
    fun `watchedFlow_emitsInitialState`() =
        runTest {
            store.setWatched("p1", "prd-X", true)
            store.watchedFlow("p1").test {
                assertEquals(setOf("prd-X"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `watchedFlow_emitsOnChange`() =
        runTest {
            store.watchedFlow("p1").test {
                assertEquals(emptySet(), awaitItem())
                store.setWatched("p1", "prd-Y", true)
                assertEquals(setOf("prd-Y"), awaitItem())
                store.setWatched("p1", "prd-Y", false)
                assertEquals(emptySet(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
