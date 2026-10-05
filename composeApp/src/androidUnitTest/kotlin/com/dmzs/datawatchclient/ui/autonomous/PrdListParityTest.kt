package com.dmzs.datawatchclient.ui.autonomous

import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PWA list sort tiebreak + card progress bar (05 › List sort, Card: progress bar). */
class PrdListParityTest {
    private fun prd(
        id: String,
        updated: String? = null,
        created: String? = null,
        done: Int = 0,
        total: Int = 0,
    ) = PrdDto(
        id = id,
        updatedAt = updated,
        createdAt = created,
        stories =
            listOf(
                PrdStoryDto(
                    id = "s1",
                    tasks = List(total) { i -> PrdTaskDto(id = "t$i", status = if (i < done) "completed" else "pending") },
                ),
            ),
    )

    @Test
    fun `activity key prefers updated_at over created_at`() =
        assertTrue(
            prdActivityKey(prd("a", updated = "2026-10-04T10:00:00Z", created = "2026-01-01T00:00:00Z")) >
                prdActivityKey(prd("b", created = "2026-10-03T00:00:00Z")),
        )

    @Test
    fun `unparseable timestamps sort last`() = assertEquals(0L, prdActivityKey(prd("c", updated = "nope")))

    @Test
    fun `task progress counts completed tasks`() = assertEquals(2 to 4, prdTaskProgress(prd("d", done = 2, total = 4)))

    @Test
    fun `no tasks means no progress bar`() = assertNull(prdTaskProgress(prd("e")))
}
