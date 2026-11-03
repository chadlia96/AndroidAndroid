package com.android.androidAndroid.agent.model


open class ActionExecutionResult(
    val agentAction: AgentAction,
    val target: String,
    val success: Boolean,
    val message: String
) {
    override fun toString(): String {
        return "ActionExecutionResult(agentAction=$agentAction, target=$target, success=$success, message=$message)"
    }
}
