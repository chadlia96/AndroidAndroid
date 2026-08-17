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

class DescribeUiActionExecutionResult(
    agentAction: AgentAction,
    target: String,
    success: Boolean,
    message: String,
    val detailDescription: String
) : ActionExecutionResult(agentAction, target, success, message)
