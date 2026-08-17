package com.android.androidAndroid.agent

import com.android.androidAndroid.logging.AppLogger as Log
import com.android.androidAndroid.agent.model.AgentAction
import com.android.androidAndroid.agent.model.ActionExecutionResult
import com.android.androidAndroid.accessibility.model.ScreenNode
import com.android.androidAndroid.ui.overlay.FloatingButtonsOverlayHelper
import com.android.androidAndroid.ui.overlay.FloatingTextOverlayHelper
import com.android.androidAndroid.accessibility.AccessibilityActionExecutor
import com.android.androidAndroid.accessibility.monitor.NodeOperateHelper
import com.android.androidAndroid.agent.model.AgentDecision
import com.android.androidAndroid.agent.action.describeUi.UiDescriptionExecutor
import com.android.androidAndroid.agent.prompt.AgentPromptBuilder
import com.android.androidAndroid.agent.prompt.ScreenStatRenderer
import com.android.androidAndroid.llm.LLMChat
import kotlinx.coroutines.delay

object AgentLoop {

    private const val LLM_TRACE_TAG = "LLM_TRACE"
    private const val MAX_ACTIONS_PER_ROUND = 1
    private const val PARSE_FAILURE_LIMIT = 3
    private const val EXEC_FAILURE_LIMIT = 3
    private const val ROUND_DELAY_MS = 700L
    private const val UI_STABLE_MS = 2_000L
    private const val UI_STABLE_MAX_WAIT_MS = 15_000L
    private const val UI_STABLE_POLL_MS = 100L
    private const val MANUAL_NEXT_ROUND_POLL_MS = 100L

    private var session: AgentSession? = null
    @Volatile
    private var manualNextRoundClicked = false

    suspend fun send(input: String): AgentSession {
        val currentSession = session
            ?.takeUnless { it.status.isTerminal() }
            ?: AgentSession(goal = input).also {
                if (!installManualNextRoundClick(it)) {
                    failSession(it, "init_manual_click", "悬浮按钮不可用,无法手动推进循环")
                }
                session = it
            }

        currentSession.metadata["last_user_input"] = input
        TaskWatcher.start(currentSession.goal)
        return run(currentSession)
    }

    fun clear() {
        session?.let {
            it.status = AgentSessionStatus.CANCELLED
            it.updatedAt = System.currentTimeMillis()
            it.finishedAt = it.updatedAt
            clearManualNextRoundClick(it)
        }
        session = null
    }

    private suspend fun run(session: AgentSession): AgentSession {
        if (session.status == AgentSessionStatus.SUCCEEDED ||
            session.status == AgentSessionStatus.FAILED ||
            session.status == AgentSessionStatus.CANCELLED
        ) {
            return session
        }

        var execFailures = 0
        val loopContext = LoopContext(session)

        session.status = AgentSessionStatus.RUNNING
        session.updatedAt = System.currentTimeMillis()
        Log.i(LLM_TRACE_TAG, "AGENT_LOOP_START: goalChars=${session.goal.length}")

        try {
            while (session.status == AgentSessionStatus.RUNNING && session.turnCount < session.maxTurns) {
                // 手动推进: 每一轮开始前重置点击状态,等待悬浮按钮点击后继续。
                /*Log.i(LLM_TRACE_TAG, "ROUND_WAIT_MANUAL_CLICK(turn=${session.turnCount})")
                val continued = waitForManualNextRound(session)
                if (!continued) {
                    failSession(session, "wait_manual_click", "悬浮按钮不可用,无法手动推进循环")
                    break
                }*/
                Log.i(LLM_TRACE_TAG, "ROUND_START(turn=${session.turnCount})")

                // 首轮直接启动目标应用, 跳过状态采集和 prompt 判断
                if (session.turnCount == 0) {
                    loopContext.agentActions = listOf(AgentAction.LaunchApp)
                    executeActions(loopContext)
                    if (session.status != AgentSessionStatus.RUNNING) break
                    waitForUiSettle()
                    continue
                }

                if (!observeCurrentState(loopContext)) break
                if (!makeDecision(loopContext)) {
                    if (session.status != AgentSessionStatus.RUNNING) break
                    continue
                }
                if (!dispatchActions(loopContext)) {
                    if (session.status != AgentSessionStatus.RUNNING) break
                    continue
                }
                executeActions(loopContext)

                // 轮次收尾: 如果执行阶段已经改变 session 状态,直接退出主循环。
                if (session.status != AgentSessionStatus.RUNNING) {
                    break
                }
                // 等待界面稳定(连续 2 秒无变化)后再进入下一轮
                if (loopContext.agentActions.lastOrNull() != AgentAction.DescribeUi) {
                    waitForUiSettle()
                }
            }

            // 预算停止: 超过最大轮次仍未成功,视为失败并记录停止原因。
            if (session.status == AgentSessionStatus.RUNNING) {
                failSession(session, "max_turns", "达到最大轮次仍未成功")
            }
        } finally {
            clearManualNextRoundClick(session)
        }
        Log.i(
            LLM_TRACE_TAG,
            "AGENT_LOOP_END: status=${session.status}, turn=${session.turnCount}/${session.maxTurns}, " +
                "exit=${session.metadata["last_exit_point"].orEmpty()}, error=${session.metadata["last_error"].orEmpty()}"
        )
        return session
    }

    private suspend fun observeCurrentState(context: LoopContext): Boolean {
        val root = NodeOperateHelper.getCurrentRootCopy()
        if (root == null) {
            failSession(context.session, "observe_root_null", "无法获取当前界面信息")
            return false
        }

        try {
            context.currentState = NodeOperateHelper.getSnapshot(root)
            context.stateDescription = ScreenStatRenderer.render(context.currentState)
            context.appDescription = NodeOperateHelper.getCurrentAppInfo(root)
            context.toastDescription = NodeOperateHelper.consumeToastText()
        } finally {
            root.recycle()
        }

        if (context.currentState.isEmpty() && context.stateDescription.isBlank()) {
            failSession(context.session, "observe_empty_state", "当前界面为空")
            return false
        }
        Log.i(
            LLM_TRACE_TAG,
            "OBSERVE_DONE(nodes=${context.currentState.size}, stateChars=${context.stateDescription.length})"
        )
        return true
    }

    private suspend fun makeDecision(context: LoopContext): Boolean {
        val prompt = AgentPromptBuilder.buildFinalPrompt(
            context.session,
            context.stateDescription,
            context.appDescription,
            context.toastDescription
        )
        TaskWatcher.onLLMRequest()
        val rawResponse = queryRawResponse(prompt)
        TaskWatcher.onLLMResponse(rawResponse)
        context.response = parseResponse(rawResponse)
        context.session.messages.add(rawResponse)
        Log.i(
            LLM_TRACE_TAG,
            "MODEL_OUTPUT(status=${context.response?.status}, actions=${context.response?.agentActions?.size ?: 0}, rawChars=${rawResponse.length})"
        )
        if (context.response == null) {
            context.parseFailures += 1
            context.session.metadata["parse_failures"] = context.parseFailures.toString()
            context.session.metadata["last_raw_response"] = rawResponse.take(500)
            Log.w(
                LLM_TRACE_TAG,
                "PARSE_FAILED(count=${context.parseFailures}/$PARSE_FAILURE_LIMIT, rawChars=${rawResponse.length})"
            )
            if (context.parseFailures >= PARSE_FAILURE_LIMIT) {
                failSession(context.session, "parse_failure_limit", "LLM响应解析失败次数过多")
            } else {
                delay(ROUND_DELAY_MS)
            }
            return false
        }

        context.session.metadata["last_reason"] = context.response?.reason.orEmpty()
        if (context.response?.status == "success") {
            Log.i(
                LLM_TRACE_TAG,
                "SESSION_SUCCESS_BY_MODEL(reasonChars=${context.response?.reason.orEmpty().length})"
            )
            TaskWatcher.onSuccess()
            markSucceeded(context.session)
            return false
        }
        return true
    }

    private suspend fun dispatchActions(context: LoopContext): Boolean {
        context.agentActions = context.response?.agentActions?.take(MAX_ACTIONS_PER_ROUND).orEmpty()
        if (context.agentActions.isNotEmpty()) {
            context.parseFailures = 0
            return true
        }

        context.parseFailures += 1
        context.session.metadata["parse_failures"] = context.parseFailures.toString()
        Log.w(LLM_TRACE_TAG, "EMPTY_ACTIONS(count=${context.parseFailures}/$PARSE_FAILURE_LIMIT)")
        if (context.parseFailures >= PARSE_FAILURE_LIMIT) {
            failSession(context.session, "empty_actions_limit", "动作为空且未成功")
        } else {
            delay(ROUND_DELAY_MS)
        }
        return false
    }

    private suspend fun executeActions(context: LoopContext) {
        for (action in context.agentActions) {
            context.session.turnCount += 1
            context.session.updatedAt = System.currentTimeMillis()

            val result = executeAction(action, context.session, context.currentState)
            context.session.actionExecutionResults.add(result)
            Log.i(
                LLM_TRACE_TAG,
                "ACTION_RESULT(turn=${context.session.turnCount}, " +
                    "action=${action.javaClass.simpleName}, success=${result.success})"
            )

            if (action == AgentAction.LaunchApp) break
            if (context.session.turnCount >= context.session.maxTurns) break
        }
    }

    private suspend fun waitForUiSettle() {
        // 动作执行后等待界面稳定: 重置时间戳后轮询,界面连续 UI_STABLE_MS 无变化则视为操作结束
        NodeOperateHelper.markUiChanged()
        val deadline = System.currentTimeMillis() + UI_STABLE_MAX_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (NodeOperateHelper.isUiStable(UI_STABLE_MS)) return
            delay(UI_STABLE_POLL_MS)
        }
    }

    private class LoopContext(
        val session: AgentSession,
        var parseFailures: Int = 0,
        var currentState: List<ScreenNode> = emptyList(),
        var stateDescription: String = "",
        var appDescription: String = "",
        var toastDescription: String = "",
        var response: AgentDecision? = null,
        var agentActions: List<AgentAction> = emptyList()
    )

    private suspend fun queryRawResponse(prompt: String): String {
        FloatingTextOverlayHelper.isThinking(true)
        return try {
            LLMChat.deepseekChat(prompt, true)
        } finally {
            FloatingTextOverlayHelper.isThinking(false)
        }
    }

    private fun parseResponse(raw: String): AgentDecision? {
        return AgentResponseParser.parse(raw)
    }

    private fun installManualNextRoundClick(session: AgentSession): Boolean {
        manualNextRoundClicked = false
        return FloatingButtonsOverlayHelper.setClick1 {
            manualNextRoundClicked = true
            session.metadata["waiting_for_overlay_click"] = "false"
            Log.i(LLM_TRACE_TAG, "MANUAL_NEXT_ROUND(turn=${session.turnCount})")
        }
    }

    private fun clearManualNextRoundClick(session: AgentSession) {
        FloatingButtonsOverlayHelper.clear1()
        manualNextRoundClicked = false
        session.metadata["waiting_for_overlay_click"] = "false"
    }

    private suspend fun waitForManualNextRound(session: AgentSession): Boolean {
        manualNextRoundClicked = false
        session.metadata["waiting_for_overlay_click"] = "true"
        while (session.status == AgentSessionStatus.RUNNING && !manualNextRoundClicked) {
            if (!FloatingButtonsOverlayHelper.isShowing()) {
                session.metadata["waiting_for_overlay_click"] = "false"
                return false
            }
            delay(MANUAL_NEXT_ROUND_POLL_MS)
        }
        session.metadata["waiting_for_overlay_click"] = "false"
        return session.status == AgentSessionStatus.RUNNING
    }

    private suspend fun executeAction(
        agentAction: AgentAction,
        session: AgentSession,
        currentState: List<ScreenNode>
    ): ActionExecutionResult {
        if (agentAction == AgentAction.DescribeUi) {
            return UiDescriptionExecutor.execute()
        }
        val targetNode = when (agentAction) {
            is AgentAction.Click -> currentState.getOrNull(agentAction.target - 1)
            is AgentAction.Input -> currentState.getOrNull(agentAction.target - 1)
            is AgentAction.Swipe, is AgentAction.LaunchApp, AgentAction.Back,
            AgentAction.DescribeUi -> null
        }
        val executionResult = AccessibilityActionExecutor.execute(agentAction, targetNode, session.goal)
        Log.i(
            LLM_TRACE_TAG,
            "EXECUTE_ACTION(turn=${session.turnCount}, action=${agentAction.javaClass.simpleName}, " +
                "success=${executionResult.success})"
        )
        val target = when (agentAction) {
            is AgentAction.Click, is AgentAction.Input -> targetNode?.message.orEmpty()
            is AgentAction.Swipe, AgentAction.Back -> ""
            AgentAction.LaunchApp -> executionResult.target
            AgentAction.DescribeUi -> ""
        }
        return ActionExecutionResult(
            agentAction = agentAction,
            target = target,
            success = executionResult.success,
            message = executionResult.message
        )
    }

    private fun markSucceeded(session: AgentSession) {
        session.status = AgentSessionStatus.SUCCEEDED
        session.updatedAt = System.currentTimeMillis()
        session.finishedAt = session.updatedAt
    }

    private fun markFailed(session: AgentSession) {
        session.status = AgentSessionStatus.FAILED
        session.updatedAt = System.currentTimeMillis()
        session.finishedAt = session.updatedAt
    }

    private fun failSession(session: AgentSession, exitPoint: String, reason: String) {
        session.metadata["last_exit_point"] = exitPoint
        session.metadata["last_error"] = reason
        Log.w(
            LLM_TRACE_TAG,
            "SESSION_FAILED(exit=$exitPoint, reason=$reason, turn=${session.turnCount}/${session.maxTurns})"
        )
        markFailed(session)
    }

    private fun AgentSessionStatus.isTerminal(): Boolean {
        return this == AgentSessionStatus.SUCCEEDED ||
            this == AgentSessionStatus.FAILED ||
            this == AgentSessionStatus.CANCELLED
    }
}
