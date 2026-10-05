package com.dmzs.datawatchclient.ui.dashboard

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** Parity D34a — PWA `_dashUpdateStatBar` aggregation. */
class DashboardStatStripTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private fun session(
        id: String,
        state: SessionState,
    ) = Session(
        id = id,
        serverProfileId = "p",
        state = state,
        createdAt = Instant.fromEpochMilliseconds(0),
        lastActivityAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `aggregates sessions tasks and verdicts`() {
        val boards =
            listOf(
                obj(
                    """{"telemetry":{"tasks":[{"status":"completed"},{"status":"running"}],
                       "guardrail_verdicts":[{"outcome":"block"},{"outcome":"warn"},{"outcome":"pass"}]}}""",
                ),
                obj("""{"telemetry":{"tasks":[{"status":"completed"}]}}"""),
                obj("""{}"""),
            )
        val s =
            dashStatStrip(
                listOf(session("a", SessionState.Running), session("b", SessionState.Waiting), session("c", SessionState.Completed)),
                boards,
                1.25,
                2,
            )
        assertEquals(DashStatStrip(3, 2, 2, 3, 1, 1, 1.25, 2), s)
    }

    @Test
    fun `cost prefers total then sums sessions`() {
        assertEquals(3.5, costTotalUsd(obj("""{"total_cost_usd":3.5}""")))
        assertEquals(1.5, costTotalUsd(obj("""{"sessions":[{"est_cost_usd":1.0},{"est_cost_usd":0.5}]}""")))
        assertEquals(0.0, costTotalUsd(obj("""{}""")))
    }
}
