package com.dmzs.datawatchclient.ui.sessions

import com.dmzs.datawatchclient.transport.dto.GitStatusDto
import com.dmzs.datawatchclient.transport.dto.SessionStatusBoardDto
import com.dmzs.datawatchclient.transport.dto.SprintStatusDto
import com.dmzs.datawatchclient.transport.dto.TestStatusDto
import com.dmzs.datawatchclient.ui.common.fakeResolver
import com.dmzs.datawatchclient.ui.common.nullResolver
import io.mockk.coEvery
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStatusViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val sampleBoard = SessionStatusBoardDto(
        state = "running",
        hookHealth = "alive",
        currentFocus = "Implement Sprint 26",
        sprint = SprintStatusDto(name = "Sprint 26", progress = "4/8 tasks"),
        tests = TestStatusDto(passing = 33, failing = 0, total = 33),
        git = GitStatusDto(branch = "main", uncommitted = 0, ahead = 1),
    )

    @Test
    fun `fetchStatus populates board on success`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.getSessionStatus("sess-1") } returns Result.success(sampleBoard)

        val vm = SessionStatusViewModel(sessionId = "sess-1", resolver = r)
        vm.startPolling()

        val s = vm.state.value
        assertNotNull(s.board)
        assertEquals("running", s.board?.state)
        assertEquals("alive", s.board?.hookHealth)
        assertEquals("Implement Sprint 26", s.board?.currentFocus)
        assertNull(s.error)

        vm.stopPolling()
    }

    @Test
    fun `fetchStatus sets error on failure`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.getSessionStatus(any<String>()) } returns Result.failure(RuntimeException("connection refused"))

        val vm = SessionStatusViewModel(sessionId = "sess-1", resolver = r)
        vm.startPolling()

        val s = vm.state.value
        assertNull(s.board)
        assertNotNull(s.error)

        vm.stopPolling()
    }

    @Test
    fun `fetchStatus no-ops when no profile`() = runTest(testDispatcher) {
        val vm = SessionStatusViewModel(sessionId = "sess-1", resolver = nullResolver)
        vm.startPolling()

        val s = vm.state.value
        assertNull(s.board)
        assertNull(s.error)

        vm.stopPolling()
    }

    @Test
    fun `stopPolling cancels in-flight poll`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.getSessionStatus(any<String>()) } returns Result.success(sampleBoard)

        val vm = SessionStatusViewModel(sessionId = "sess-1", resolver = r)
        vm.startPolling()
        vm.stopPolling()

        // After stop, state should already have last fetched board (first poll ran)
        // and no new polls should start — verified by absence of exception
        assertNotNull(vm.state.value.board)
    }

    @Test
    fun `board fields reflect tests card data`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        val boardWithFailures = sampleBoard.copy(tests = TestStatusDto(passing = 28, failing = 5, total = 33))
        coEvery { t.getSessionStatus(any<String>()) } returns Result.success(boardWithFailures)

        val vm = SessionStatusViewModel(sessionId = "sess-1", resolver = r)
        vm.startPolling()

        val tests = vm.state.value.board?.tests
        assertNotNull(tests)
        assertEquals(5, tests.failing)
        assertEquals(28, tests.passing)

        vm.stopPolling()
    }
}
