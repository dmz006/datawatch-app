package com.dmzs.datawatchclient.ui.autonomous

import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdListDto
import com.dmzs.datawatchclient.ui.common.fakeResolver
import com.dmzs.datawatchclient.ui.common.nullResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AutonomousViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh populates prds on success`() =
        runTest(testDispatcher) {
            val (t, r) = fakeResolver()
            val prds = listOf(PrdDto(id = "p1", name = "x", status = "needs_review"))
            coEvery { t.listPrds() } returns Result.success(PrdListDto(prds = prds))

            val vm = AutonomousViewModel(r)
            vm.refresh()

            val s = vm.state.value
            assertEquals(false, s.loading)
            assertEquals(prds, s.prds)
            assertEquals(null, s.banner)
        }

    @Test
    fun `refresh sets banner on transport failure`() =
        runTest(testDispatcher) {
            val (t, r) = fakeResolver()
            coEvery { t.listPrds() } returns Result.failure(RuntimeException("boom"))
            val vm = AutonomousViewModel(r)

            vm.refresh()

            assertTrue(vm.state.value.banner!!.contains("boom"))
            assertEquals(emptyList(), vm.state.value.prds)
        }

    @Test
    fun `refresh shows no-server banner when resolver returns null`() =
        runTest(testDispatcher) {
            val vm = AutonomousViewModel(nullResolver)
            vm.refresh()
            assertEquals("No enabled server.", vm.state.value.banner)
        }

    @Test
    fun `create posts the request body and triggers a refresh`() =
        runTest(testDispatcher) {
            val (t, r) = fakeResolver()
            val req = NewPrdRequestDto(name = "x", projectDir = "/code")
            val captured = slot<NewPrdRequestDto>()
            coEvery { t.createPrd(capture(captured)) } returns Result.success("prd-new")
            coEvery { t.listPrds() } returns Result.success(PrdListDto())

            val vm = AutonomousViewModel(r)
            vm.create(req)

            assertEquals(req, captured.captured)
            coVerify(exactly = 1) { t.createPrd(any()) }
            coVerify(atLeast = 1) { t.listPrds() }
        }

    @Test
    fun `approve calls prdAction approve and refreshes`() =
        runTest(testDispatcher) {
            val (t, r) = fakeResolver()
            coEvery { t.prdAction(any(), any(), any()) } returns Result.success(Unit)
            coEvery { t.listPrds() } returns Result.success(PrdListDto())

            val vm = AutonomousViewModel(r)
            vm.approve("prd-1")

            coVerify { t.prdAction("prd-1", "approve", null) }
        }

    @Test
    fun `reject sends the reason in the body`() =
        runTest(testDispatcher) {
            val (t, r) = fakeResolver()
            coEvery { t.prdAction(any(), any(), any()) } returns Result.success(Unit)
            coEvery { t.listPrds() } returns Result.success(PrdListDto())

            val vm = AutonomousViewModel(r)
            vm.reject("prd-1", "not coherent")

            coVerify {
                t.prdAction(
                    "prd-1",
                    "reject",
                    match { it.toString().contains("not coherent") },
                )
            }
        }

    @Test
    fun `editStory passes only blank-stripped fields through`() =
        runTest(testDispatcher) {
            val (t, r) = fakeResolver()
            coEvery { t.editStory(any(), any(), any(), any(), any()) } returns Result.success(Unit)
            coEvery { t.listPrds() } returns Result.success(PrdListDto())

            val vm = AutonomousViewModel(r)
            vm.editStory(prdId = "prd-1", storyId = "s1", newTitle = "  ", newDescription = "new")

            coVerify { t.editStory("prd-1", "s1", null, "new", null) }
        }

    @Test
    fun `editFiles forwards the files list`() =
        runTest(testDispatcher) {
            val (t, r) = fakeResolver()
            coEvery { t.editFiles(any(), any(), any(), any(), any()) } returns Result.success(Unit)
            coEvery { t.listPrds() } returns Result.success(PrdListDto())

            val vm = AutonomousViewModel(r)
            vm.editFiles(prdId = "prd-1", storyId = "s1", files = listOf("a.go", "b.go"))

            coVerify { t.editFiles("prd-1", "s1", null, listOf("a.go", "b.go"), null) }
        }
}
