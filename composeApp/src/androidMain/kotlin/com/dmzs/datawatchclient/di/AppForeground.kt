package com.dmzs.datawatchclient.di

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Process-wide "is any activity visible" signal for the polling loops.
 *
 * The 2026-10-14 production profile showed every poller (alerts every 5 s,
 * Dashboard every 10 s, Sessions every 30 s, Automata every 5 s) kept running
 * while the app was in the background. Loops call [awaitForeground] before
 * each request so they idle in the background and resume on return.
 *
 * Must be [install]ed on the main thread (ProcessLifecycleOwner requirement);
 * DatawatchApp does that in onCreate.
 */
public object AppForeground {
    private val _visible = MutableStateFlow(false)
    public val visible: StateFlow<Boolean> = _visible.asStateFlow()

    @Volatile private var installed = false

    public fun install() {
        if (installed) return
        installed = true
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        _visible.value = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    _visible.value = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    _visible.value = false
                }
            },
        )
    }

    /** Suspends until the app is visible. Returns at once when it already is. */
    public suspend fun awaitForeground() {
        if (!installed) return
        visible.first { it }
    }
}
