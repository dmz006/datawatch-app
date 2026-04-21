package com.dmzs.datawatchclient.push

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks which session the user is currently viewing (via
 * [SessionDetailScreen]) and whether the app process is in the
 * foreground. [NotificationPoster] consults both to suppress
 * wake notifications for a session the user is already
 * looking at — matches the PWA's "don't show a bell if you're
 * on this tab" behaviour.
 */
public object ForegroundSessionTracker {
    private val currentSessionId: AtomicReference<String?> = AtomicReference(null)
    private val foreground: AtomicBoolean = AtomicBoolean(false)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) { foreground.set(true) }
                override fun onStop(owner: LifecycleOwner) { foreground.set(false) }
            },
        )
    }

    public fun enter(sessionId: String) {
        currentSessionId.set(sessionId)
    }

    public fun leave(sessionId: String) {
        // Only clear if the leaving screen is the same one we recorded;
        // protects against reorder races on rapid navigation.
        currentSessionId.compareAndSet(sessionId, null)
    }

    /**
     * True when the user is viewing [sessionId] AND the app is in
     * the foreground. Callers should suppress push notifications
     * for such sessions since the content is already on screen.
     */
    public fun isForeground(sessionId: String): Boolean =
        foreground.get() && currentSessionId.get() == sessionId
}
