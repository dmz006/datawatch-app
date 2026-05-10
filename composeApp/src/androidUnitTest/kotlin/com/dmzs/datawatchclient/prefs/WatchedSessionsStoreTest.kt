package com.dmzs.datawatchclient.prefs

import android.content.Context
import android.content.SharedPreferences
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

/**
 * Unit tests for [WatchedSessionsStore].
 *
 * Uses a [FakeSharedPreferences] backed by an in-memory map so tests run on
 * the JVM without Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchedSessionsStoreTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var store: WatchedSessionsStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fakePrefs = FakeSharedPreferences()
        val ctx = mockk<Context>()
        every { ctx.getSharedPreferences(any(), any()) } returns fakePrefs
        store = WatchedSessionsStore(ctx)
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
        store.setWatched("p1", "sess-1", true)
        assertTrue(store.isWatched("p1", "sess-1"))
        assertEquals(setOf("sess-1"), store.watchedIds("p1"))
    }

    @Test
    fun `setWatched_false_removesFromSet`() {
        store.setWatched("p1", "sess-1", true)
        store.setWatched("p1", "sess-1", false)
        assertFalse(store.isWatched("p1", "sess-1"))
        assertTrue(store.watchedIds("p1").isEmpty())
    }

    @Test
    fun `profileIsolation_separateSetsPerProfile`() {
        store.setWatched("p1", "sess-1", true)
        store.setWatched("p2", "sess-2", true)

        assertTrue(store.isWatched("p1", "sess-1"))
        assertFalse(store.isWatched("p1", "sess-2"))
        assertFalse(store.isWatched("p2", "sess-1"))
        assertTrue(store.isWatched("p2", "sess-2"))
    }

    @Test
    fun `watchedFlow_emitsInitialState`() = runTest {
        store.setWatched("p1", "sess-A", true)
        store.watchedFlow("p1").test {
            assertEquals(setOf("sess-A"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `watchedFlow_emitsOnChange`() = runTest {
        store.watchedFlow("p1").test {
            assertEquals(emptySet(), awaitItem()) // initial
            store.setWatched("p1", "sess-X", true)
            assertEquals(setOf("sess-X"), awaitItem())
            store.setWatched("p1", "sess-X", false)
            assertEquals(emptySet(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/**
 * Minimal in-memory [SharedPreferences] for JVM unit tests.
 * Supports StringSet only (sufficient for [WatchedSessionsStore]).
 */
private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (map[key] as? Set<*>)?.mapNotNull { it as? String }?.toSet() ?: defValues?.toSet()

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += l
    }

    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= l
    }

    private fun notify(key: String) = listeners.forEach { it.onSharedPreferenceChanged(this, key) }

    // Unused overrides — WatchedSessionsStore only uses StringSet.
    override fun getAll(): Map<String, *> = map
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)

    inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            pending[key] = values?.toSet()
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removals += key
            return this
        }

        override fun apply() {
            val changed = mutableSetOf<String>()
            removals.forEach { k ->
                if (map.remove(k) != null) changed += k
            }
            pending.forEach { (k, v) ->
                map[k] = v
                changed += k
            }
            changed.forEach { notify(it) }
        }

        override fun commit(): Boolean { apply(); return true }

        // Unused
        override fun putString(key: String, value: String?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
        override fun clear(): SharedPreferences.Editor = this
    }
}
