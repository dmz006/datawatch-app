package com.dmzs.datawatchclient.ui

import com.dmzs.datawatchclient.transport.dto.MemoryExtractFactsResponseDto
import com.dmzs.datawatchclient.transport.dto.MemorySpellcheckResponseDto
import com.dmzs.datawatchclient.transport.dto.MemorySweepStaleResponseDto
import com.dmzs.datawatchclient.transport.dto.ObserverClusterDto
import com.dmzs.datawatchclient.transport.dto.ObserverClusterNodeDto
import com.dmzs.datawatchclient.transport.dto.ObserverEbpfDto
import com.dmzs.datawatchclient.transport.dto.ObserverHostDto
import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto
import com.dmzs.datawatchclient.transport.dto.ObserverPeerHostDto
import com.dmzs.datawatchclient.transport.dto.ObserverPeersDto
import com.dmzs.datawatchclient.transport.dto.ObserverStatsDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorEdgeDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorGraphDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorNodeDto
import com.dmzs.datawatchclient.transport.dto.PluginDto
import com.dmzs.datawatchclient.transport.dto.PluginsDto
import com.dmzs.datawatchclient.transport.dto.SpellcheckSuggestionDto
import com.dmzs.datawatchclient.transport.dto.SvoTripleDto
import com.dmzs.datawatchclient.ui.common.fakeResolver
import com.dmzs.datawatchclient.ui.memory.MempalaceActionsViewModel
import com.dmzs.datawatchclient.ui.monitoring.ClusterNodesViewModel
import com.dmzs.datawatchclient.ui.monitoring.EBpfStatusViewModel
import com.dmzs.datawatchclient.ui.monitoring.FederatedPeersViewModel
import com.dmzs.datawatchclient.ui.monitoring.PluginsCardViewModel
import com.dmzs.datawatchclient.ui.orchestrator.OrchestratorGraphViewModel
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
class MonitoringViewModelTests {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    // ---- FederatedPeersViewModel ----

    @Test
    fun `federated peers loads + filters`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.observerPeers() } returns
            Result.success(
                ObserverPeersDto(
                    peers = listOf(
                        ObserverPeerDto(name = "n1", shape = "standalone"),
                        ObserverPeerDto(
                            name = "n2",
                            shape = "agent",
                            hostInfo = ObserverPeerHostDto(shape = "agent"),
                        ),
                    ),
                ),
            )

        val vm = FederatedPeersViewModel(r)
        vm.refresh()
        assertEquals(2, vm.state.value.peers.size)
        assertEquals(false, vm.state.value.loading)

        vm.setFilter(FederatedPeersViewModel.Filter.Agent)
        assertEquals(FederatedPeersViewModel.Filter.Agent, vm.state.value.filter)
    }

    @Test
    fun `federated peers swallows transport failure into empty list`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.observerPeers() } returns Result.failure(RuntimeException("no observer"))
        val vm = FederatedPeersViewModel(r)
        vm.refresh()
        assertEquals(emptyList(), vm.state.value.peers)
        assertNotNull(vm.state.value.error)
    }

    // ---- ClusterNodesViewModel ----

    @Test
    fun `cluster nodes maps stats cluster nodes`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        val node = ObserverClusterNodeDto(name = "k8s-1", ready = true, podCount = 3)
        coEvery { t.observerStats() } returns
            Result.success(ObserverStatsDto(cluster = ObserverClusterDto(nodes = listOf(node))))
        val vm = ClusterNodesViewModel(r)
        vm.refresh()
        assertEquals(listOf(node), vm.state.value.nodes)
    }

    @Test
    fun `cluster nodes empty when stats has no cluster block`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.observerStats() } returns Result.success(ObserverStatsDto())
        val vm = ClusterNodesViewModel(r)
        vm.refresh()
        assertEquals(emptyList(), vm.state.value.nodes)
    }

    // ---- EBpfStatusViewModel ----

    @Test
    fun `ebpf status pulls host ebpf block`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        val ebpf = ObserverEbpfDto(configured = true, capability = true, kprobesLoaded = false)
        coEvery { t.observerStats() } returns
            Result.success(ObserverStatsDto(host = ObserverHostDto(ebpf = ebpf)))
        val vm = EBpfStatusViewModel(r)
        vm.refresh()
        assertEquals(ebpf, vm.state.value.ebpf)
    }

    @Test
    fun `ebpf null when daemon predates observer endpoint`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.observerStats() } returns Result.failure(RuntimeException("404"))
        val vm = EBpfStatusViewModel(r)
        vm.refresh()
        assertNull(vm.state.value.ebpf)
    }

    // ---- PluginsCardViewModel ----

    @Test
    fun `plugins splits subprocess from native`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        val sub = PluginDto(name = "git-hook", kind = "subprocess")
        val nat = PluginDto(name = "datawatch-observer", kind = "native")
        coEvery { t.listPlugins() } returns
            Result.success(PluginsDto(plugins = listOf(sub), native = listOf(nat)))
        val vm = PluginsCardViewModel(r)
        vm.refresh()
        assertEquals(listOf(sub), vm.state.value.plugins)
        assertEquals(listOf(nat), vm.state.value.native)
        assertEquals(false, vm.state.value.loading)
    }

    // ---- OrchestratorGraphViewModel ----

    @Test
    fun `orchestrator graph stores nodes and edges`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        val graph =
            OrchestratorGraphDto(
                id = "g1",
                nodes = listOf(OrchestratorNodeDto(id = "n1", status = "running")),
                edges = listOf(OrchestratorEdgeDto(from = "n1", to = "n2")),
            )
        coEvery { t.orchestratorGraph("g1") } returns Result.success(graph)
        val vm = OrchestratorGraphViewModel(r)
        vm.refresh("g1")
        assertEquals(graph, vm.state.value.graph)
    }

    @Test
    fun `orchestrator graph banner on failure`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.orchestratorGraph(any()) } returns Result.failure(RuntimeException("404"))
        val vm = OrchestratorGraphViewModel(r)
        vm.refresh("g1")
        assertNull(vm.state.value.graph)
        assertNotNull(vm.state.value.banner)
    }

    // ---- MempalaceActionsViewModel ----

    @Test
    fun `sweep stale stores count from response`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        coEvery { t.memorySweepStale(any(), any()) } returns Result.success(7)
        val vm = MempalaceActionsViewModel(r)
        vm.setSweepDays("30")
        vm.runSweep()
        assertEquals(7, vm.state.value.sweepResult)
        assertEquals(false, vm.state.value.busy)
    }

    @Test
    fun `spellcheck stores suggestions from response`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        val sug = listOf(SpellcheckSuggestionDto(word = "helo", suggestions = listOf("hello")))
        coEvery { t.memorySpellcheck(any(), any()) } returns Result.success(sug)
        val vm = MempalaceActionsViewModel(r)
        vm.setSpellcheckText("helo world")
        vm.runSpellcheck()
        assertEquals(sug, vm.state.value.spellcheckResult)
    }

    @Test
    fun `extract facts stores triples from response`() = runTest(testDispatcher) {
        val (t, r) = fakeResolver()
        val tr = listOf(SvoTripleDto(subject = "Alice", verb = "merged", obj = "PR-42"))
        coEvery { t.memoryExtractFacts(any()) } returns Result.success(tr)
        val vm = MempalaceActionsViewModel(r)
        vm.setFactsText("Alice merged PR-42 today.")
        vm.runExtractFacts()
        assertEquals(tr, vm.state.value.factsResult)
    }
}
