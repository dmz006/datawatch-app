package com.dmzs.datawatchclient.prefs

import android.content.SharedPreferences

/**
 * Minimal in-memory [SharedPreferences] for JVM unit tests.
 * Supports StringSet only (sufficient for WatchedSessionsStore / WatchedAutomataStore).
 */
internal class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? = (map[key] as? Set<*>)?.mapNotNull { it as? String }?.toSet() ?: defValues?.toSet()

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += l
    }

    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= l
    }

    private fun notify(key: String) = listeners.forEach { it.onSharedPreferenceChanged(this, key) }

    override fun getAll(): Map<String, *> = map

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = map[key] as? String ?: defValue

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = (map[key] as? Int) ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = (map[key] as? Long) ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = (map[key] as? Float) ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = (map[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = map.containsKey(key)

    inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ): SharedPreferences.Editor {
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

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
        override fun clear(): SharedPreferences.Editor = this
    }
}
