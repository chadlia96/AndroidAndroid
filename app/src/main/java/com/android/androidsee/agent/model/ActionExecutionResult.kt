package com.android.androidAndroid.agent.model

data class ActionExecutionResult(
    val agentAction: AgentAction,
    val target: String,
    val success: Boolean,
    val message: String
)