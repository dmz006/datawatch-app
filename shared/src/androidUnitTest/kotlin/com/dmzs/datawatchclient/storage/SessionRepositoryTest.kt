package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * In-memory SQLite tests for [SessionRepository].
 * Each test seeds a parent server_profile row first (FK constraint).
 */
class SessionRepositoryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: DatawatchDb
    private lateinit var profileRepo: ServerProfileRepository
    private lateinit var sessionRepo: SessionRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DatawatchDb.Schema.create(driver)
        // SQLite disables FK enforcement by default; the Android driver enables it via
        // SupportSQLiteOpenHelper. Mirror that here so cascade-delete tests are realistic.
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        db = DatawatchDb(driver)
        profileRepo = ServerProfileRepository(db, dispatcher)
        sessionRepo = SessionRepository(db, dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seedProfile(id: String = "srv-1") {
        profileRepo.upsert(
            ServerProfile(
                id = id,
                displayName = "Test Server",
                baseUrl = "https://localhost:8443",
                bearerTokenRef = "dw.profile.$id",
                trustAnchorSha256 = null,
                reachabilityProfileId = "lan",
                createdTs = 0L,
            ),
        )
    }

    private fun session(
        id: String = "sess-1",
        profileId: String = "srv-1",
        state: SessionState = SessionState.Running,
        task: String = "fix bug",
        muted: Boolean = false,
        lastActivityMs: Long = 5000L,
    ) = Session(
        id = id,
        serverProfileId = profileId,
        hostnamePrefix = "laptop",
        state = state,
        taskSummary = task,
        createdAt = Instant.fromEpochMilliseconds(1000L),
        lastActivityAt = Instant.fromEpochMilliseconds(lastActivityMs),
        muted = muted,
        name = null,
        backend = null,
        lastPrompt = null,
        promptContext = null,
        lastResponse = null,
        outputMode = null,
        inputMode = null,
        agentId = null,
    )

    @Test
    fun `upsert then observeForProfile returns the session`() =
        runTest(dispatcher) {
            seedProfile()
            sessionRepo.upsert(session())
            sessionRepo.observeForProfile("srv-1").test {
                val list = awaitItem()
                assertEquals(1, list.size)
                val s = list.first()
                assertEquals("sess-1", s.id)
                assertEquals(SessionState.Running, s.state)
                assertEquals("fix bug", s.taskSummary)
                assertFalse(s.muted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsert replaces existing session`() =
        runTest(dispatcher) {
            seedProfile()
            sessionRepo.upsert(session(state = SessionState.Running))
            sessionRepo.upsert(session(state = SessionState.Waiting))
            sessionRepo.observeForProfile("srv-1").test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals(SessionState.Waiting, list.first().state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeForProfile only returns sessions for the given profile`() =
        runTest(dispatcher) {
            seedProfile("srv-1")
            seedProfile("srv-2")
            sessionRepo.upsert(session(id = "s1", profileId = "srv-1"))
            sessionRepo.upsert(session(id = "s2", profileId = "srv-2"))
            sessionRepo.observeForProfile("srv-1").test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("s1", list.first().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `replaceAll atomically replaces the session list`() =
        runTest(dispatcher) {
            seedProfile()
            sessionRepo.upsert(session(id = "old-1"))
            sessionRepo.upsert(session(id = "old-2"))
            val fresh =
                listOf(
                    session(id = "new-1", task = "task A"),
                    session(id = "new-2", task = "task B"),
                )
            sessionRepo.replaceAll("srv-1", fresh)
            sessionRepo.observeForProfile("srv-1").test {
                val list = awaitItem()
                assertEquals(2, list.size)
                val ids = list.map { it.id }.toSet()
                assertTrue(ids.contains("new-1"))
                assertTrue(ids.contains("new-2"))
                assertFalse(ids.contains("old-1"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `replaceAll with empty list clears sessions for profile`() =
        runTest(dispatcher) {
            seedProfile()
            sessionRepo.upsert(session())
            sessionRepo.replaceAll("srv-1", emptyList())
            sessionRepo.observeForProfile("srv-1").test {
                assertEquals(emptyList(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setMuted flips muted flag without touching other fields`() =
        runTest(dispatcher) {
            seedProfile()
            sessionRepo.upsert(session(muted = false))
            sessionRepo.setMuted("sess-1", true)
            sessionRepo.observeForProfile("srv-1").test {
                val s = awaitItem().first()
                assertTrue(s.muted)
                assertEquals("fix bug", s.taskSummary)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setMuted can toggle back to false`() =
        runTest(dispatcher) {
            seedProfile()
            sessionRepo.upsert(session(muted = true))
            sessionRepo.setMuted("sess-1", false)
            sessionRepo.observeForProfile("srv-1").test {
                assertFalse(awaitItem().first().muted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sessions are ordered by last_activity_ts descending`() =
        runTest(dispatcher) {
            seedProfile()
            sessionRepo.upsert(session(id = "a", lastActivityMs = 1000L))
            sessionRepo.upsert(session(id = "b", lastActivityMs = 3000L))
            sessionRepo.upsert(session(id = "c", lastActivityMs = 2000L))
            sessionRepo.observeForProfile("srv-1").test {
                val ids = awaitItem().map { it.id }
                assertEquals(listOf("b", "c", "a"), ids)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `profile cascade delete removes child sessions`() =
        runTest(dispatcher) {
            seedProfile("srv-del")
            sessionRepo.upsert(session(id = "s-del", profileId = "srv-del"))
            profileRepo.delete("srv-del")
            sessionRepo.observeForProfile("srv-del").test {
                assertEquals(emptyList(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
