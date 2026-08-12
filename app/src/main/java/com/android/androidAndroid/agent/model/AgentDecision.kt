package com.android.androidAndroid.agent.model

data class AgentDecision(
    val status: String,
    val agentActions: List<AgentAction>,
    val reason: String? = null,
    val rawResponse: String? = null,

    )
