package com.android.androidAndroid.agent

import com.android.androidAndroid.agent.model.ActionExecutionResult

enum class AgentSessionStatus {
    INIT,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

data class AgentSession(
    val sessionId: String = SESSION_ID,
    val goal: String,
    var status: AgentSessionStatus = AgentSessionStatus.INIT,
    var messages: MutableList<String> = mutableListOf(),
    var actionExecutionResults: MutableList<ActionExecutionResult> = mutableListOf(),
    var turnCount: Int = 0,
    val maxTurns: Int = DEFAULT_MAX_TURNS,
    val metadata: MutableMap<String, String> = mutableMapOf(),
    val startedAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = startedAt,
    var finishedAt: Long? = null
) {
    companion object {
        const val SESSION_ID = "AGENT_LOOP"
        const val DEFAULT_MAX_TURNS = 30
    }
}
