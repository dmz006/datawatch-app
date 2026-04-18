package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.SessionDto
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {

    @Test
    fun `SessionDto maps to domain Session with correct state and timestamps`() {
        // Wire format per parent datawatch openapi.yaml: task / hostname /
        // RFC3339 created_at / updated_at.
        val dto = SessionDto(
            id = "srv-a3f2",
            state = "running",
            task = "Upgrade deps",
            hostname = "workstation",
            createdAt = "2023-11-14T22:13:20Z",
            updatedAt = "2023-11-14T22:14:20Z",
        )

        val session = dto.toDomain(serverProfileId = "srv-1")

        assertEquals("srv-a3f2", session.id)
        assertEquals("srv-1", session.serverProfileId)
        assertEquals("workstation", session.hostnamePrefix)
        assertEquals(SessionState.Running, session.state)
        assertEquals("Upgrade deps", session.taskSummary)
        assertEquals(
            Instant.parse("2023-11-14T22:13:20Z"),
            session.createdAt,
        )
        assertEquals(
            Instant.parse("2023-11-14T22:14:20Z"),
            session.lastActivityAt,
        )
    }

    @Test
    fun `unknown state from server maps to Error without throwing`() {
        val dto = SessionDto(id = "x", state = "freshly_invented_state")
        assertEquals(SessionState.Error, dto.toDomain("srv-1").state)
    }

    @Test
    fun `missing timestamps fall back to DISTANT_PAST instead of crashing`() {
        val dto = SessionDto(
            id = "x",
            state = "new",
            createdAt = null,
            updatedAt = null,
        )
        val session = dto.toDomain("srv-1")
        assertEquals(Instant.DISTANT_PAST, session.createdAt)
        assertEquals(Instant.DISTANT_PAST, session.lastActivityAt)
    }
}
