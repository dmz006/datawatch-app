package com.dmzs.datawatchclient.push

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionStateWatcherTest {
    private val t0 = 1_000_000L

    @BeforeTest
    fun reset() = SessionStateWatcher.reset()

    private fun session(
        id: String,
        state: SessionState,
        prompt: String? = null,
    ) = Session(
        id = id,
        serverProfileId = "test",
        state = state,
        createdAt = Instant.fromEpochMilliseconds(0),
        lastActivityAt = Instant.fromEpochMilliseconds(0),
        lastPrompt = prompt,
    )

    private fun update(
        sessions: List<Session>,
        nowMs: Long,
    ): Pair<List<NotificationPoster.Event>, List<String>> =
        SessionStateWatcher.computeUpdates(sessions, nowMs)

    // -- Settle window --

    @Test
    fun `transition to Waiting does not notify before settle window`() {
        // Seed
        update(listOf(session("s1", SessionState.Running)), t0)
        // Transition into Waiting
        val (posts, _) = update(listOf(session("s1", SessionState.Waiting, "help?")), t0 + 1_000)
        assertTrue(posts.isEmpty(), "Should not notify before settle window")
    }

    @Test
    fun `fires notification after settle window elapses`() {
        update(listOf(session("s1", SessionState.Running)), t0)
        update(listOf(session("s1", SessionState.Waiting, "help?")), t0 + 1_000)
        val (posts, _) = update(listOf(session("s1", SessionState.Waiting, "help?")), t0 + SessionStateWatcher.SETTLE_MS + 1_000)
        assertEquals(1, posts.size)
        assertEquals("s1", posts[0].sessionId)
    }

    // -- Running/waiting/running flap within settle window --

    @Test
    fun `flap running-waiting-running within settle window sends nothing`() {
        update(listOf(session("s1", SessionState.Running)), t0)
        // Into waiting
        update(listOf(session("s1", SessionState.Waiting, "q")), t0 + 5_000)
        // Back to running before settle window
        val (posts, _) = update(listOf(session("s1", SessionState.Running)), t0 + 10_000)
        assertTrue(posts.isEmpty())
    }

    @Test
    fun `flap and return to Waiting resets the episode clock`() {
        update(listOf(session("s1", SessionState.Running)), t0)
        update(listOf(session("s1", SessionState.Waiting, "q")), t0 + 5_000)
        // Flap back to running
        update(listOf(session("s1", SessionState.Running)), t0 + 10_000)
        // Return to Waiting — episode restarts from this point
        update(listOf(session("s1", SessionState.Waiting, "q")), t0 + 15_000)
        // Not yet at SETTLE_MS from the new episode start
        val (posts1, _) = update(listOf(session("s1", SessionState.Waiting, "q")), t0 + 30_000)
        assertTrue(posts1.isEmpty(), "Should not notify before new episode settle window")
        // Now past SETTLE_MS from new episode start
        val (posts2, _) = update(listOf(session("s1", SessionState.Waiting, "q")), t0 + 15_000 + SessionStateWatcher.SETTLE_MS + 1_000)
        assertEquals(1, posts2.size, "Should notify after new episode settle window")
    }

    // -- Reconnect: session briefly vanishes then returns in Waiting --

    @Test
    fun `reconnect does not re-notify same prompt`() {
        update(listOf(session("s1", SessionState.Running)), t0)
        update(listOf(session("s1", SessionState.Waiting, "help?")), t0 + 1_000)
        // Settle window passed — fires once
        val (posts1, _) = update(listOf(session("s1", SessionState.Waiting, "help?")), t0 + SessionStateWatcher.SETTLE_MS + 1_000)
        assertEquals(1, posts1.size)
        // Session briefly vanishes (network blip)
        update(emptyList(), t0 + SessionStateWatcher.SETTLE_MS + 5_000)
        // Returns still in Waiting with same prompt
        val (posts2, _) = update(listOf(session("s1", SessionState.Waiting, "help?")), t0 + SessionStateWatcher.SETTLE_MS + 10_000)
        assertTrue(posts2.isEmpty(), "Reconnect with same prompt must not re-notify")
    }

    // -- Prompt dedup --

    @Test
    fun `same prompt is not notified twice in same episode`() {
        update(listOf(session("s1", SessionState.Running)), t0)
        update(listOf(session("s1", SessionState.Waiting, "q1")), t0 + 1_000)
        val tFire = t0 + SessionStateWatcher.SETTLE_MS + 1_000
        val (posts1, _) = update(listOf(session("s1", SessionState.Waiting, "q1")), tFire)
        assertEquals(1, posts1.size)
        // Same prompt, later poll
        val (posts2, _) = update(listOf(session("s1", SessionState.Waiting, "q1")), tFire + 10_000)
        assertTrue(posts2.isEmpty(), "Same prompt must not re-notify")
    }

    @Test
    fun `new prompt after reply notifies again`() {
        update(listOf(session("s1", SessionState.Running)), t0)
        update(listOf(session("s1", SessionState.Waiting, "q1")), t0 + 1_000)
        val tFire = t0 + SessionStateWatcher.SETTLE_MS + 1_000
        update(listOf(session("s1", SessionState.Waiting, "q1")), tFire)
        // User replied
        SessionStateWatcher.onReplied("s1")
        // Session goes to Running, back to Waiting with new prompt
        update(listOf(session("s1", SessionState.Running)), tFire + 1_000)
        update(listOf(session("s1", SessionState.Waiting, "q2")), tFire + 5_000)
        val (posts, _) = update(listOf(session("s1", SessionState.Waiting, "q2")), tFire + 5_000 + SessionStateWatcher.SETTLE_MS + 1_000)
        assertEquals(1, posts.size)
        assertEquals("q2", posts[0].body)
    }

    // -- Cold start: already-waiting sessions don't fire --

    @Test
    fun `cold start waiting session does not notify`() {
        // First call is seeding — session is already Waiting
        update(listOf(session("s1", SessionState.Waiting, "preexisting")), t0)
        // Subsequent poll — session still Waiting, well past settle window
        val (posts, _) = update(listOf(session("s1", SessionState.Waiting, "preexisting")), t0 + SessionStateWatcher.SETTLE_MS + 10_000)
        assertTrue(posts.isEmpty(), "Cold-start waiting session must not re-notify")
    }

    // -- Cancellation --

    @Test
    fun `leaving Waiting schedules cancellation`() {
        update(listOf(session("s1", SessionState.Running)), t0)
        update(listOf(session("s1", SessionState.Waiting, "q")), t0 + 1_000)
        val (_, cancel) = update(listOf(session("s1", SessionState.Running)), t0 + 5_000)
        assertTrue(cancel.contains("s1"), "Leaving Waiting must cancel notification")
    }
}
