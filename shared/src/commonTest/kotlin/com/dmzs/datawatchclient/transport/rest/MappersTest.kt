package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.SessionDto
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {

    @Test
    fun `SessionDto maps to domain Session with correct state and timestamps`() {
        val dto = SessionDto(
            id = "srv-a3f2",
            state = "running",
            taskSummary = "Upgrade deps",
            hostnamePrefix = "workstation",
            createdTs = 1_700_000_000_000L,
            lastActivityTs = 1_700_000_060_000L,
        )

        val session = dto.toDomain(serverProfileId = "srv-1")

        assertEquals("srv-a3f2", session.id)
        assertEquals("srv-1", session.serverProfileId)
        assertEquals("workstation", session.hostnamePrefix)
        assertEquals(SessionState.Running, session.state)
        assertEquals("Upgrade deps", session.taskSummary)
        assertEquals(1_700_000_000_000L, session.createdAt.toEpochMilliseconds())
        assertEquals(1_700_000_060_000L, session.lastActivityAt.toEpochMilliseconds())
    }

    @Test
    fun `unknown state from server maps to Error without throwing`() {
        val dto = SessionDto(
            id = "x",
            state = "freshly_invented_state",
            createdTs = 0L,
            lastActivityTs = 0L,
        )
        assertEquals(SessionState.Error, dto.toDomain("srv-1").state)
    }
}
