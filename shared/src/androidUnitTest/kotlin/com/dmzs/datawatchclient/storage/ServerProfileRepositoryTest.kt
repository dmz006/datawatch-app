package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In-memory SQLite tests for [ServerProfileRepository].
 * Uses [JdbcSqliteDriver] (plain SQLite, no SQLCipher) to verify
 * business logic independently of Android platform and encryption.
 */
class ServerProfileRepositoryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: DatawatchDb
    private lateinit var repo: ServerProfileRepository

    private val fixedClock = object : Clock {
        var nowMs = 1_000_000L
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DatawatchDb.Schema.create(driver)
        db = DatawatchDb(driver)
        repo = ServerProfileRepository(db, dispatcher, fixedClock)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun profile(
        id: String = "srv-1",
        displayName: String = "Local",
        baseUrl: String = "https://localhost:8443",
        enabled: Boolean = true,
        lastSeenTs: Long? = null,
    ) = ServerProfile(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
        bearerTokenRef = "dw.profile.$id",
        trustAnchorSha256 = null,
        reachabilityProfileId = "lan",
        enabled = enabled,
        createdTs = 1_000L,
        lastSeenTs = lastSeenTs,
    )

    @Test
    fun `upsert then observeAll returns the profile`() =
        runTest(dispatcher) {
            repo.upsert(profile())
            repo.observeAll().test {
                val list = awaitItem()
                assertEquals(1, list.size)
                val p = list.first()
                assertEquals("srv-1", p.id)
                assertEquals("Local", p.displayName)
                assertEquals("https://localhost:8443", p.baseUrl)
                assertTrue(p.enabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsert is idempotent — replace updates the row`() =
        runTest(dispatcher) {
            repo.upsert(profile())
            repo.upsert(profile(displayName = "Updated"))
            repo.observeAll().test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("Updated", list.first().displayName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `delete removes the profile from the list`() =
        runTest(dispatcher) {
            repo.upsert(profile(id = "srv-1"))
            repo.upsert(profile(id = "srv-2", displayName = "Remote"))
            repo.delete("srv-1")
            repo.observeAll().test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("srv-2", list.first().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `delete non-existent id is a no-op`() =
        runTest(dispatcher) {
            repo.upsert(profile())
            repo.delete("does-not-exist")
            repo.observeAll().test {
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeAll returns empty list when table is empty`() =
        runTest(dispatcher) {
            repo.observeAll().test {
                assertEquals(emptyList(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `touchLastSeen updates last_seen_ts to clock now`() =
        runTest(dispatcher) {
            fixedClock.nowMs = 9_999L
            repo.upsert(profile(id = "srv-1", lastSeenTs = null))
            fixedClock.nowMs = 42_000L
            repo.touchLastSeen("srv-1")
            repo.observeAll().test {
                val p = awaitItem().first()
                assertEquals(42_000L, p.lastSeenTs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `touchLastSeen on unknown id is a no-op`() =
        runTest(dispatcher) {
            repo.upsert(profile())
            repo.touchLastSeen("unknown")
            repo.observeAll().test {
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `enabled flag round-trips correctly`() =
        runTest(dispatcher) {
            repo.upsert(profile(enabled = false))
            repo.observeAll().test {
                assertEquals(false, awaitItem().first().enabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `multiple profiles are ordered by last_seen_ts desc then name`() =
        runTest(dispatcher) {
            repo.upsert(profile(id = "a", displayName = "Alpha", lastSeenTs = 100L))
            repo.upsert(profile(id = "b", displayName = "Beta", lastSeenTs = 200L))
            repo.upsert(profile(id = "c", displayName = "Gamma", lastSeenTs = null))
            repo.observeAll().test {
                val ids = awaitItem().map { it.id }
                // Gamma has null lastSeenTs → pushed to end; Beta > Alpha by ts
                assertEquals(listOf("b", "a", "c"), ids)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
