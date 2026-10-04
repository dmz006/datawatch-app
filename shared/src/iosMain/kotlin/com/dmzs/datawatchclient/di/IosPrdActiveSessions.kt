package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.SessionStatusBoardDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** One live session working on a PRD (PWA prd-active-session-row). */
public data class IosPrdActiveSessionRow(
    val session: Session,
    val board: SessionStatusBoardDto?,
    val storyTitle: String?,
    val taskTitle: String?,
    /** Compute node the session runs on, when the session names one. */
    val node: ComputeNodeDetailDto?,
)

/**
 * PRD active-session card data (parity B17; PWA _loadPRDActiveSessionCard).
 * Matches non-terminal sessions whose `prd_id` is this PRD or whose id is a
 * task's `session_id`; fetches the status board + compute node detail for
 * the first three. Swift polls every 5 s while the detail is visible.
 */
public object IosPrdActiveSessions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun load(
        profile: ServerProfile,
        prd: PrdDto,
        onResult: (List<IosPrdActiveSessionRow>?) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val sessions = t.listSessions().getOrNull()
            if (sessions == null) {
                onResult(null)
                return@launch
            }
            val byTask = mutableMapOf<String, Pair<String?, String?>>()
            prd.stories.forEach { story ->
                story.tasks.forEach { task ->
                    val sid = task.sessionId
                    if (!sid.isNullOrBlank()) byTask[sid] = (story.title.ifBlank { story.id }) to task.task.ifBlank { null }
                }
            }
            val matches =
                sessions.filter { s ->
                    val linked = s.prdId == prd.id || byTask.containsKey(s.fullId) || byTask.containsKey(s.id)
                    linked && !s.isTerminal
                }.take(3)
            val rows =
                coroutineScope {
                    matches.map { s ->
                        async {
                            val board = async { t.getSessionStatus(s.id).getOrNull() }
                            val node = async { s.computeNodeRef?.let { t.getComputeNodeDetail(it).getOrNull() } }
                            val ctx = byTask[s.fullId] ?: byTask[s.id]
                            IosPrdActiveSessionRow(s, board.await(), ctx?.first, ctx?.second, node.await())
                        }
                    }.awaitAll()
                }
            onResult(rows)
        }
    }
}
